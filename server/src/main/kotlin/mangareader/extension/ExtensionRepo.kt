package mangareader.extension

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.protobuf.ProtoBuf
import mangareader.AppConfig
import mangareader.JsonCodec
import okio.buffer
import okio.gzip
import uy.kohesive.injekt.injectLazy

/**
 * Fetches and caches the extension lists of the configured repositories.
 * Supports the three formats Suwayomi supports: legacy index.min.json, repo.json -> index_v2,
 * and the protobuf/json "store" index.
 */
object ExtensionRepo {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()

    private val mutex = Mutex()

    @Volatile
    private var cache: List<RepoExtension>? = null

    @Volatile
    private var cachedAt = 0L

    private const val TTL_MS = 30 * 60 * 1000L

    suspend fun list(forceRefresh: Boolean = false): List<RepoExtension> {
        cache?.let { if (!forceRefresh && System.currentTimeMillis() - cachedAt < TTL_MS) return it }
        return mutex.withLock {
            cache?.let { if (!forceRefresh && System.currentTimeMillis() - cachedAt < TTL_MS) return@withLock it }
            val all =
                AppConfig.extensionRepos.flatMap { repo ->
                    try {
                        fetchRepo(repo)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.error(e) { "Failed to fetch extension repo $repo" }
                        emptyList()
                    }
                }
            // A repo may list the same package twice; keep the newest
            val deduped =
                all
                    .groupBy { it.pkgName }
                    .map { (_, versions) -> versions.maxBy { it.versionCode } }
                    .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
            cache = deduped
            cachedAt = System.currentTimeMillis()
            deduped
        }
    }

    suspend fun find(pkgName: String): RepoExtension? =
        list().find { it.pkgName == pkgName } ?: list(forceRefresh = true).find { it.pkgName == pkgName }

    private suspend fun download(url: String): ByteArray {
        val response = network.client.newCall(GET(url)).awaitSuccess()
        return response.body.source().use { src ->
            val isGzip =
                src.peek().let { peeked ->
                    try {
                        peeked.readShort().toInt() and 0xffff == 0x1f8b
                    } catch (_: Exception) {
                        false
                    }
                }
            if (isGzip) src.gzip().buffer().readByteArray() else src.readByteArray()
        }
    }

    private suspend fun fetchRepo(
        url: String,
        depth: Int = 0,
    ): List<RepoExtension> {
        check(depth < 4) { "Too many redirects between repo indexes" }
        val bytes = download(url)
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }

        return when (first) {
            // "[" -> legacy index.min.json
            '['.code.toByte() -> {
                val base = url.substringBeforeLast('/')
                JsonCodec.json
                    .decodeFromString<List<LegacyIndexEntry>>(bytes.decodeToString())
                    .map { it.toRepoExtension(base, url) }
            }

            // "{" -> repo.json or a json store index
            '{'.code.toByte() -> {
                val text = bytes.decodeToString()
                val legacy = runCatching { JsonCodec.json.decodeFromString<LegacyRepoJson>(text) }.getOrNull()
                when {
                    legacy?.indexV2 != null -> {
                        fetchRepo(legacy.indexV2, depth + 1)
                    }

                    legacy != null -> {
                        val base = url.substringBeforeLast('/')
                        fetchRepo("$base/index.min.json", depth + 1)
                    }

                    else -> {
                        storeExtensions(JsonCodec.json.decodeFromString<StoreIndex>(text), url, depth)
                    }
                }
            }

            // anything else -> protobuf store index
            else -> {
                storeExtensions(ProtoBuf.decodeFromByteArray<StoreIndex>(bytes), url, depth)
            }
        }
    }

    private suspend fun storeExtensions(
        store: StoreIndex,
        url: String,
        depth: Int,
    ): List<RepoExtension> {
        val list =
            store.extensionList
                ?: store.extensionListUrl?.let { listUrl ->
                    val bytes = download(listUrl)
                    if (bytes.firstOrNull() == '{'.code.toByte()) {
                        JsonCodec.json.decodeFromString<StoreIndex.ExtensionList>(bytes.decodeToString())
                    } else {
                        ProtoBuf.decodeFromByteArray<StoreIndex.ExtensionList>(bytes)
                    }
                }
                ?: return emptyList()
        return list.extensions.map { it.toRepoExtension(url) }
    }

    private fun LegacyIndexEntry.toRepoExtension(
        base: String,
        repo: String,
    ): RepoExtension {
        val nsfwFlag = nsfw == 1
        return RepoExtension(
            pkgName = pkg,
            name = name.substringAfter("Tachiyomi: "),
            versionName = version,
            versionCode = code,
            lang = lang,
            nsfw = nsfwFlag,
            apkUrl = "$base/apk/$apk",
            jarUrl = null,
            iconUrl = "$base/icon/$pkg.png",
            repo = repo,
            sources =
                sources.orEmpty().map { RepoSource(id = it.id.toString(), name = it.name, lang = it.lang) },
        )
    }

    private fun StoreIndex.Extension.toRepoExtension(repo: String): RepoExtension {
        val langs = sources.map { it.language }.toSet()
        return RepoExtension(
            pkgName = packageName,
            name = name.substringAfter("Tachiyomi: "),
            versionName = versionName,
            versionCode = versionCode,
            lang = if (langs.size == 1) langs.first() else "all",
            nsfw = contentWarning == StoreIndex.ContentWarning.NSFW || contentWarning == StoreIndex.ContentWarning.MIXED,
            apkUrl = resources.apkUrl,
            jarUrl = resources.jarUrl,
            iconUrl = resources.iconUrl,
            repo = repo,
            sources = sources.map { RepoSource(id = it.id.toString(), name = it.name, lang = it.language) },
        )
    }
}
