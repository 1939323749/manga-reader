#!/usr/bin/env python3
"""End-to-end smoke test against a running manga-reader server.

Usage: smoke_test.py [base_url]

Installs a couple of extensions (one from the prebuilt jar, one through the
APK -> dex2jar path), then walks popular list -> manga -> chapter -> page image
for each, and checks search filters and source preferences on MangaDex.
Exits non-zero if any required check fails.
"""

import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:4567").rstrip("/")

# (package, preferApk, source name filter, required)
TARGETS = [
    ("eu.kanade.tachiyomi.extension.all.mangadex", False, lambda s: s["lang"] == "en", True),
    ("eu.kanade.tachiyomi.extension.en.mangapill", False, lambda s: True, True),
    # APK -> dex2jar path: known to break on R8-optimized APKs (see README), so optional
    ("eu.kanade.tachiyomi.extension.en.mangapill", True, lambda s: True, False),
    ("eu.kanade.tachiyomi.extension.en.weebcentral", False, lambda s: True, False),
]

failures = []


def call(method, path, body=None, timeout=180, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            content = resp.read()
            if raw:
                return resp.headers.get("Content-Type", ""), content
            return json.loads(content) if content else None
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:500]
        raise RuntimeError(f"{method} {path} -> HTTP {e.code}: {detail}") from None


def step(name, fn):
    t = time.time()
    try:
        result = fn()
        print(f"  ok   {name} ({time.time() - t:.1f}s)")
        return result
    except Exception as e:  # noqa: BLE001
        print(f"  FAIL {name}: {e}")
        raise


def wait_for_server():
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            if call("GET", "/api/health", timeout=5)["status"] == "ok":
                return
        except Exception:  # noqa: BLE001
            time.sleep(2)
    raise RuntimeError("server did not come up")


def expect_status(method, path, body, status):
    try:
        call(method, path, body)
    except RuntimeError as e:
        assert f"HTTP {status}" in str(e), str(e)
        return
    raise AssertionError(f"{method} {path} should have failed with {status}")


def walk_filters(filters):
    """Yield (filter, index, childIndex) for every filter, including group children."""
    for f in filters:
        yield f, f["index"], None
        for c in f.get("filters") or []:
            yield c, f["index"], c["index"]


def exercise_filters(sid):
    filters = step("filter list", lambda: call("GET", f"/api/sources/{sid}/filters"))
    kinds = sorted({f["type"] for f, _, _ in walk_filters(filters)})
    print(f"       -> {len(filters)} filters, types: {', '.join(kinds)}")
    assert filters, "no filters"
    assert [f["index"] for f in filters] == list(range(len(filters))), "indices are not positions"
    assert "unknown" not in kinds, "unrecognised filter type"

    base = step("search with default filters", lambda: call(
        "POST", f"/api/sources/{sid}/search", {"q": "", "page": 1, "filters": []}))
    assert base["mangas"], "default filtered search is empty"

    # include the first tag of a group of tri-states (MangaDex: tags/genres)
    tri = next(((f, i, c) for f, i, c in walk_filters(filters) if f["type"] == "tristate" and c is not None), None)
    assert tri, "no tri-state filter inside a group"
    f, i, c = tri
    change = [{"index": i, "childIndex": c, "state": 1}]
    tagged = step(f"search including '{f['name']}'", lambda: call(
        "POST", f"/api/sources/{sid}/search", {"q": "", "page": 1, "filters": change}))
    print(f"       -> {len(tagged['mangas'])} results")
    assert tagged["mangas"], "filtered search is empty"
    before = {m["url"] for m in base["mangas"]}
    after = {m["url"] for m in tagged["mangas"]}
    assert before != after, "filter had no effect on results"

    sort = next((f for f in filters if f["type"] == "sort"), None)
    if sort:
        step(f"search sorted by '{sort['values'][-1]}'", lambda: call(
            "POST", f"/api/sources/{sid}/search",
            {"q": "", "page": 1, "filters": [{"index": sort["index"], "state": {"index": len(sort["values"]) - 1, "ascending": True}}]}))

    step("bad filter index -> 400", lambda: expect_status(
        "POST", f"/api/sources/{sid}/search", {"q": "", "filters": [{"index": 9999, "state": 1}]}, 400))


def exercise_preferences(sid):
    url = f"/api/sources/{sid}/preferences"
    prefs = step("preferences", lambda: call("GET", url))
    assert prefs["configurable"], "source should be configurable"
    items = prefs["preferences"]
    print(f"       -> {len(items)} settings: {', '.join(sorted({p['type'] for p in items}))}")
    assert items, "no preferences"

    def value_of(resp, key):
        return next(p["value"] for p in resp["preferences"] if p["key"] == key)

    switch = next((p for p in items if p["type"] in ("switch", "checkbox") and p["key"]), None)
    assert switch, "no switch preference"
    old = bool(switch["value"])
    after = step(f"toggle '{switch['title']}'", lambda: call("PUT", url, {"key": switch["key"], "value": not old}))
    assert value_of(after, switch["key"]) is (not old), "switch value did not change"
    assert value_of(call("GET", url), switch["key"]) is (not old), "switch value not persisted"
    call("PUT", url, {"key": switch["key"], "value": old})

    lst = next((p for p in items if p["type"] == "list" and p["key"] and len(p["entryValues"] or []) > 1), None)
    if lst:
        cur = lst["value"]
        other = next(v for v in lst["entryValues"] if v != cur)
        after = step(f"set '{lst['title']}' = {other}", lambda: call("PUT", url, {"key": lst["key"], "value": other}))
        assert value_of(after, lst["key"]) == other, "list value did not change"
        step("invalid list value -> 400", lambda: expect_status(
            "PUT", url, {"key": lst["key"], "value": "definitely-not-an-option"}, 400))
        call("PUT", url, {"key": lst["key"], "value": cur})

    step("unknown key -> 404", lambda: expect_status("PUT", url, {"key": "no-such-key", "value": "x"}, 404))
    # the extension was reloaded after each change; it must still work
    again = step("popular after reload", lambda: call("GET", f"/api/sources/{sid}/popular?page=1"))
    assert again["mangas"], "popular list empty after preference change"


def exercise(pkg, prefer_apk, pick_source):
    q = "?preferApk=1" if prefer_apk else ""
    ext = step(f"install {pkg}{' (apk/dex2jar)' if prefer_apk else ''}",
               lambda: call("POST", f"/api/extensions/{pkg}/install{q}", timeout=600))
    print(f"       -> {ext['name']} {ext['versionName']}, {len(ext['sources'])} source(s)")

    sources = [s for s in call("GET", "/api/sources") if s["pkgName"] == pkg and pick_source(s)]
    assert sources, "no matching source"
    sid = sources[0]["id"]

    info = step("source info", lambda: call("GET", f"/api/sources/{sid}"))
    print(f"       -> {info['name']} ({info['lang']}), latest={info['supportsLatest']}")

    popular = step("popular page 1", lambda: call("GET", f"/api/sources/{sid}/popular?page=1"))
    assert popular["mangas"], "popular list is empty"
    print(f"       -> {len(popular['mangas'])} manga, hasNextPage={popular['hasNextPage']}")

    search = step("search 'one'", lambda: call("GET", f"/api/sources/{sid}/search?q=one&page=1"))
    print(f"       -> {len(search['mangas'])} results")

    if pkg.endswith(".mangadex") and not prefer_apk:
        exercise_filters(sid)
        exercise_preferences(sid)

    for manga in popular["mangas"][:3]:
        detail = step(f"details '{manga['title'][:40]}'",
                      lambda m=manga: call("POST", f"/api/sources/{sid}/manga", m))
        chapters = detail["chapters"]
        print(f"       -> {len(chapters)} chapters")
        if not chapters:
            continue

        if manga.get("thumbnailUrl"):
            ctype, body = step("cover", lambda m=manga: call(
                "GET", f"/api/sources/{sid}/cover?url={urllib.parse.quote(m['thumbnailUrl'], safe='')}", raw=True))
            print(f"       -> {ctype}, {len(body)} bytes")

        for chapter in chapters[:4]:
            pages = step(f"pages '{chapter['name'][:40]}'",
                         lambda c=chapter: call("POST", f"/api/sources/{sid}/pages", c))
            print(f"       -> {len(pages)} pages")
            if not pages:
                continue
            p = pages[0]
            qs = urllib.parse.urlencode({"url": p["url"], "imageUrl": p["imageUrl"] or ""})
            ctype, body = step("page image", lambda: call("GET", f"/api/sources/{sid}/image?{qs}", raw=True))
            print(f"       -> {ctype}, {len(body)} bytes")
            assert ctype.startswith("image/") and len(body) > 1000, "not an image"
            return
    raise AssertionError("never got a page image")


def main():
    print(f"smoke test against {BASE}")
    step("server up", wait_for_server)

    repo = step("repo extension list", lambda: call("GET", "/api/repo/extensions"))
    print(f"       -> {len(repo)} extensions")
    assert len(repo) > 100, "suspiciously few extensions"

    for pkg, prefer_apk, pick, required in TARGETS:
        print(f"\n== {pkg}")
        try:
            exercise(pkg, prefer_apk, pick)
        except Exception as e:  # noqa: BLE001
            if required:
                failures.append(f"{pkg}: {e}")
            else:
                print(f"  (optional, ignored) {e}")

    print("\n== network diagnostics")
    try:
        rep = step("debug/net", lambda: call("GET", "/api/debug/net?url=" + urllib.parse.quote("https://api.mangadex.org/ping", safe="")))
        for a in rep["attempts"]:
            print(f"       {'ok ' if a['ok'] else 'ERR'} {a['name']}: {a.get('status')} {a.get('tls') or ''} {a.get('error') or ''}")
        assert any(a["ok"] and a["name"].startswith("Conscrypt") for a in rep["attempts"]), "Conscrypt TLS did not work"
    except Exception as e:  # noqa: BLE001
        failures.append(f"debug/net: {e}")

    print("\n== uninstall")
    try:
        step("uninstall weebcentral", lambda: call("POST", "/api/extensions/eu.kanade.tachiyomi.extension.en.weebcentral/uninstall"))
        installed = [e["pkgName"] for e in call("GET", "/api/extensions")]
        assert "eu.kanade.tachiyomi.extension.en.weebcentral" not in installed
    except Exception as e:  # noqa: BLE001
        failures.append(f"uninstall: {e}")

    print()
    if failures:
        print("FAILED:")
        for f in failures:
            print("  -", f)
        sys.exit(1)
    print("ALL GOOD")


if __name__ == "__main__":
    main()
