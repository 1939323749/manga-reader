package mangareader.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import mangareader.extension.ExtensionManager
import mangareader.extension.InstalledExtension
import suwayomi.tachidesk.manga.impl.util.PackageTools
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString

/**
 * Keeps loaded Source instances in memory. Extensions are loaded lazily the first time
 * one of their sources is requested (e.g. after a restart).
 */
object SourceManager {
    private val logger = KotlinLogging.logger {}

    private val sources = ConcurrentHashMap<Long, Source>()
    private val byPackage = ConcurrentHashMap<String, List<Long>>()

    fun register(
        pkgName: String,
        list: List<Source>,
    ) {
        byPackage[pkgName]?.forEach { sources.remove(it) }
        list.forEach { sources[it.id] = it }
        byPackage[pkgName] = list.map { it.id }
    }

    fun unloadExtension(
        ext: InstalledExtension,
        jar: Path,
    ) {
        byPackage.remove(ext.pkgName)?.forEach { sources.remove(it) }
        ext.sources.forEach { sources.remove(it.id.toLongOrNull()) }
        PackageTools.jarLoaderMap.remove(jar.absolutePathString())?.close()
    }

    /** Drop every cached instance; they are recreated on next access (used when the user agent changes). */
    fun clear() {
        sources.clear()
        byPackage.clear()
    }

    /** Drop the cached instances of one extension (all of its sources), e.g. after a setting changed. */
    fun invalidate(pkgName: String) {
        byPackage.remove(pkgName)?.forEach { sources.remove(it) }
    }

    fun invalidateSource(sourceId: Long) {
        val pkg = byPackage.entries.firstOrNull { sourceId in it.value }?.key
        if (pkg != null) invalidate(pkg) else sources.remove(sourceId)
    }

    suspend fun get(sourceId: Long): CatalogueSource {
        val cached = sources[sourceId]
        if (cached != null) return cached as? CatalogueSource ?: throw NotCatalogueSource(sourceId)

        val ext =
            ExtensionManager.findBySourceId(sourceId)
                ?: throw NoSuchElementException("No installed extension provides source $sourceId")
        loadExtension(ext)
        val loaded = sources[sourceId] ?: throw NoSuchElementException("Source $sourceId missing from ${ext.pkgName}")
        return loaded as? CatalogueSource ?: throw NotCatalogueSource(sourceId)
    }

    private suspend fun loadExtension(ext: InstalledExtension) {
        val jar = ExtensionManager.jarPath(ext)
        logger.debug { "Loading ${ext.pkgName} from $jar" }
        val list =
            ext.className.split(';').flatMap { className ->
                when (val instance = PackageTools.loadExtensionSources(jar, className)) {
                    is Source -> listOf(instance)
                    is SourceFactory -> instance.createSources()
                    else -> throw IllegalStateException("Unknown source class type ${instance.javaClass}")
                }
            }
        register(ext.pkgName, list)
    }

    class NotCatalogueSource(
        id: Long,
    ) : IllegalStateException("Source $id cannot be browsed")
}
