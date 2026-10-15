package mangareader.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import mangareader.JsonCodec
import mangareader.extension.ExtensionManager
import mangareader.extension.ExtensionRepo
import mangareader.source.SourceManager
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.IOException

object Api {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val IMAGE_CACHE_SECONDS = 7 * 24 * 3600

    fun RoutesConfig.defineApi() {
        // ---- extensions -------------------------------------------------------------------

        get("/api/repo/extensions") { ctx ->
            ctx.runAsync {
                val refresh = ctx.queryParam("refresh") == "1"
                val repo = ExtensionRepo.list(forceRefresh = refresh)
                val installed = ExtensionManager.installedList().associateBy { it.pkgName }
                val list =
                    repo.map { ext ->
                        val inst = installed[ext.pkgName]
                        RepoExtensionDto(
                            pkgName = ext.pkgName,
                            name = ext.name,
                            versionName = ext.versionName,
                            lang = ext.lang,
                            nsfw = ext.nsfw,
                            iconUrl = ext.iconUrl,
                            sourceCount = ext.sources.size,
                            installed = inst != null,
                            installedVersion = inst?.versionName,
                            hasUpdate = inst != null && ext.versionCode > inst.versionCode,
                        )
                    }
                ctx.sendJson(list)
            }
        }

        get("/api/extensions") { ctx ->
            ctx.sendJson(ExtensionManager.installedList())
        }

        post("/api/extensions/{pkgName}/install") { ctx ->
            ctx.runAsync {
                ctx.sendJson(ExtensionManager.install(ctx.pathParam("pkgName"), preferApk = ctx.queryParam("preferApk") == "1"))
            }
        }

        post("/api/extensions/{pkgName}/uninstall") { ctx ->
            ctx.runAsync {
                ExtensionManager.uninstall(ctx.pathParam("pkgName"))
                ctx.status(204)
            }
        }

        // ---- sources ----------------------------------------------------------------------

        get("/api/sources") { ctx ->
            val list =
                ExtensionManager.installedList().flatMap { ext ->
                    ext.sources.map { src ->
                        SourceDto(
                            id = src.id,
                            name = src.name,
                            lang = src.lang,
                            pkgName = ext.pkgName,
                            extensionName = ext.name,
                            nsfw = ext.nsfw,
                            iconUrl = ext.iconUrl,
                        )
                    }
                }
            ctx.sendJson(list)
        }

        get("/api/sources/{sourceId}") { ctx ->
            ctx.runAsync {
                val id = ctx.sourceId()
                val source = SourceManager.get(id)
                val ext = ExtensionManager.findBySourceId(id)
                ctx.sendJson(
                    SourceDto(
                        id = id.toString(),
                        name = source.name,
                        lang = source.lang,
                        pkgName = ext?.pkgName.orEmpty(),
                        extensionName = ext?.name.orEmpty(),
                        nsfw = ext?.nsfw ?: false,
                        iconUrl = ext?.iconUrl,
                        supportsLatest = source.supportsLatest,
                    ),
                )
            }
        }

        get("/api/sources/{sourceId}/popular") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId())
                ctx.sendJson(source.getPopularManga(ctx.pageParam()).toDto())
            }
        }

        get("/api/sources/{sourceId}/latest") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId())
                if (!source.supportsLatest) throw IllegalArgumentException("${source.name} has no latest list")
                ctx.sendJson(source.getLatestUpdates(ctx.pageParam()).toDto())
            }
        }

        get("/api/sources/{sourceId}/search") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId())
                val query = ctx.queryParam("q").orEmpty()
                val filters: FilterList = runCatching { source.getFilterList() }.getOrDefault(FilterList())
                ctx.sendJson(source.getSearchManga(ctx.pageParam(), query, filters).toDto())
            }
        }

        /** body: MangaDto -> manga details + chapter list */
        post("/api/sources/{sourceId}/manga") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId())
                val manga = ctx.bodyAs<MangaDto>().toSManga()
                val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
                val details =
                    update.manga.toDto().let {
                        // Keep what we already knew if the source returned blanks
                        it.copy(
                            url = it.url.ifEmpty { manga.url },
                            title = it.title.ifEmpty { manga.title },
                            thumbnailUrl = it.thumbnailUrl ?: manga.thumbnail_url,
                            initialized = true,
                        )
                    }
                ctx.sendJson(MangaWithChaptersDto(details, update.chapters.map { it.toDto() }))
            }
        }

        /** body: ChapterDto -> pages */
        post("/api/sources/{sourceId}/pages") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId())
                val chapter = ctx.bodyAs<ChapterDto>().toSChapter()
                val pages = source.getPageList(chapter)
                // "the index is ignored" by contract: re-number by position
                ctx.sendJson(pages.mapIndexed { i, p -> PageDto(i, p.url, p.imageUrl) })
            }
        }

        /** Page image. Query: url (page url), imageUrl (optional, resolved when missing) */
        get("/api/sources/{sourceId}/image") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId()) as? HttpSource
                    ?: throw IllegalArgumentException("Not an online source")
                val page =
                    Page(
                        index = 0,
                        url = ctx.queryParam("url").orEmpty(),
                        imageUrl = ctx.queryParam("imageUrl")?.takeIf { it.isNotEmpty() },
                    )
                if (page.imageUrl == null) {
                    page.imageUrl = source.getImageUrl(page)
                }
                ctx.sendImage(source.getImage(page))
            }
        }

        /** Cover image, fetched with the source's own client and headers (Referer etc.) */
        get("/api/sources/{sourceId}/cover") { ctx ->
            ctx.runAsync {
                val source = SourceManager.get(ctx.sourceId()) as? HttpSource
                    ?: throw IllegalArgumentException("Not an online source")
                val url = ctx.queryParam("url") ?: throw IllegalArgumentException("url is required")
                val response = source.client.newCall(GET(url, source.headers)).await()
                if (!response.isSuccessful) {
                    response.close()
                    throw HttpException(response.code)
                }
                ctx.sendImage(response)
            }
        }

        get("/api/health") { ctx -> ctx.sendJson(mapOf("status" to "ok")) }
    }

    fun RoutesConfig.defineErrors() {
        exception(NoSuchElementException::class.java) { e, ctx -> ctx.fail(404, e) }
        exception(IllegalArgumentException::class.java) { e, ctx -> ctx.fail(400, e) }
        exception(SourceManager.NotCatalogueSource::class.java) { e, ctx -> ctx.fail(400, e) }
        exception(HttpException::class.java) { e, ctx -> ctx.fail(502, e) }
        exception(IOException::class.java) { e, ctx -> ctx.fail(502, e) }
        exception(Exception::class.java) { e, ctx ->
            logger.error(e) { "Unhandled error on ${ctx.method()} ${ctx.path()}" }
            ctx.fail(500, e)
        }
    }

    // ---- helpers ------------------------------------------------------------------------

    private fun Context.runAsync(block: suspend () -> Unit) {
        future {
            scope.future {
                try {
                    block()
                } catch (e: Throwable) {
                    handleAsyncError(e)
                }
            }
        }
    }

    /** Exceptions inside futures don't reach Javalin's exception mappers reliably; map them here. */
    private fun Context.handleAsyncError(e: Throwable) {
        val cause = (e as? java.util.concurrent.CompletionException)?.cause ?: e
        when (cause) {
            is NoSuchElementException -> fail(404, cause)
            is SourceManager.NotCatalogueSource -> fail(400, cause)
            is IllegalArgumentException -> fail(400, cause)
            is HttpException -> fail(502, cause)
            is IOException -> fail(502, cause)
            else -> {
                logger.error(cause) { "Unhandled error on ${method()} ${path()}" }
                fail(500, cause)
            }
        }
    }

    private fun Context.fail(
        status: Int,
        e: Throwable,
    ) {
        if (status < 500) logger.debug { "${method()} ${path()} -> $status: ${e.message}" }
        status(status)
        sendJson(ErrorDto(e.message ?: e.javaClass.simpleName))
    }

    private fun Context.sourceId(): Long =
        pathParam("sourceId").toLongOrNull() ?: throw IllegalArgumentException("Invalid source id")

    private fun Context.pageParam(): Int = (queryParam("page")?.toIntOrNull() ?: 1).coerceAtLeast(1)

    private inline fun <reified T> Context.bodyAs(): T = JsonCodec.json.decodeFromString(serializer<T>(), body())

    private inline fun <reified T> Context.sendJson(value: T) {
        @Suppress("UNCHECKED_CAST")
        val ser = serializer<T>() as KSerializer<T>
        contentType("application/json; charset=utf-8")
        result(JsonCodec.json.encodeToString(ser, value))
    }

    private fun Context.sendImage(response: Response) {
        val bytes = response.use { it.body.bytes() }
        val type =
            response.header("Content-Type")?.takeIf { it.startsWith("image/") }
                ?: ImageUtil.findImageType { bytes.inputStream() }?.mime
                ?: "image/jpeg"
        contentType(type)
        header("Cache-Control", "public, max-age=$IMAGE_CACHE_SECONDS")
        result(bytes)
    }
}
