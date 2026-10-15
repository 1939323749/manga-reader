// Client-side state. The server is stateless, so anything worth keeping lives here.
//
// - mangaCache: manga objects received from list pages, so the detail page can send
//   the full object (including the source's opaque `memo`) back to the server.
//   Mirrored to sessionStorage so a page refresh still works.
// - history: recently read chapters, kept in localStorage.

const SESSION_KEY = 'mr.manga-cache'
const HISTORY_KEY = 'mr.history'
const HISTORY_LIMIT = 60

function readJson(storage, key, fallback) {
  try {
    const raw = storage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

function writeJson(storage, key, value) {
  try {
    storage.setItem(key, JSON.stringify(value))
  } catch {
    // storage full or unavailable: the app still works, it just forgets more
  }
}

const mangaCache = new Map(Object.entries(readJson(sessionStorage, SESSION_KEY, {})))

const key = (sourceId, url) => `${sourceId}\n${url}`

export function rememberManga(sourceId, manga) {
  const k = key(sourceId, manga.url)
  const prev = mangaCache.get(k)
  mangaCache.set(k, { ...prev, ...manga })
  // keep the session copy bounded
  const entries = [...mangaCache.entries()].slice(-300)
  writeJson(sessionStorage, SESSION_KEY, Object.fromEntries(entries))
}

export function lookupManga(sourceId, url) {
  return mangaCache.get(key(sourceId, url)) || { url }
}

// Chapter lists for the reader's previous/next navigation
const chapterLists = new Map()

export function rememberChapters(sourceId, mangaUrl, chapters) {
  chapterLists.set(key(sourceId, mangaUrl), chapters)
}

export function lookupChapters(sourceId, mangaUrl) {
  return chapterLists.get(key(sourceId, mangaUrl))
}

export function readHistory() {
  return readJson(localStorage, HISTORY_KEY, [])
}

export function recordRead({ sourceId, sourceName, manga, chapter }) {
  const entry = {
    sourceId,
    sourceName,
    manga: { url: manga.url, title: manga.title, thumbnailUrl: manga.thumbnailUrl },
    chapter: { url: chapter.url, name: chapter.name },
    at: Date.now(),
  }
  const rest = readHistory().filter((h) => !(h.sourceId === sourceId && h.manga.url === manga.url))
  writeJson(localStorage, HISTORY_KEY, [entry, ...rest].slice(0, HISTORY_LIMIT))
}

export function lastReadChapter(sourceId, mangaUrl) {
  return readHistory().find((h) => h.sourceId === sourceId && h.manga.url === mangaUrl)?.chapter || null
}

export function clearHistory() {
  writeJson(localStorage, HISTORY_KEY, [])
}
