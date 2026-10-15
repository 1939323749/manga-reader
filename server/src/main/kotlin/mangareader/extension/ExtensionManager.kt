package mangareader.extension

/*
 * Extension install/uninstall without a database.
 * The install pipeline (download -> dex2jar or prebuilt jar -> validate manifest -> load class)
 * is adapted from Suwayomi-Server's Extension.kt (MPL-2.0).
 */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mangareader.AppConfig
import mangareader.JsonCodec
import mangareader.source.SourceManager
import okhttp3.CacheControl
import org.apache.commons.compress.archivers.zip.ZipFile
import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_CONTENT_WARNING
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NAME
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NSFW
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_SOURCE_CLASS
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream

@Serializable
data class InstalledExtension(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val nsfw: Boolean,
    val className: String,
    val jarName: String,
    val iconUrl: String? = null,
    val sources: List<RepoSource>,
)

object ExtensionManager {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()

    private val extensionsRoot: Path = Path(AppConfig.dataRoot) / "extensions"
    private val registryFile: File = (extensionsRoot / "installed.json").toFile()
    private val tempRoot: Path = Path(System.getProperty("java.io.tmpdir")) / "manga-reader" / "extensions"

    private val mutex = Mutex()

    @Volatile
    private var installed: Map<String, InstalledExtension> = load()

    private fun load(): Map<String, InstalledExtension> {
        extensionsRoot.createDirectories()
        if (!registryFile.exists()) return emptyMap()
        return try {
            JsonCodec.json
                .decodeFromString<List<InstalledExtension>>(registryFile.readText())
                .associateBy { it.pkgName }
        } catch (e: Exception) {
            logger.error(e) { "Could not read ${registryFile.path}, starting with no extensions" }
            emptyMap()
        }
    }

    private fun save() {
        val tmp = File(registryFile.parentFile, registryFile.name + ".tmp")
        tmp.writeText(JsonCodec.pretty.encodeToString(installed.values.sortedBy { it.pkgName }))
        tmp.toPath().moveTo(registryFile.toPath(), overwrite = true)
    }

    fun installedList(): List<InstalledExtension> = installed.values.sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))

    fun get(pkgName: String): InstalledExtension? = installed[pkgName]

    fun jarPath(ext: InstalledExtension): Path = extensionsRoot / ext.jarName

    /** Which installed extension provides the source with this id */
    fun findBySourceId(sourceId: Long): InstalledExtension? {
        val id = sourceId.toString()
        return installed.values.find { ext -> ext.sources.any { it.id == id } }
    }

    suspend fun install(
        pkgName: String,
        preferApk: Boolean = false,
    ): InstalledExtension =
        mutex.withLock {
            val info =
                ExtensionRepo.find(pkgName)
                    ?: throw IllegalArgumentException("Extension $pkgName is not in any configured repository")
            val existing = installed[pkgName]
            if (existing != null && existing.versionCode >= info.versionCode && !preferApk) {
                return@withLock existing
            }

            val pkg =
                if (info.jarUrl != null && !preferApk) {
                    val jar = download(info.jarUrl, "$pkgName.jar")
                    val manifest =
                        ZipFile.builder().setPath(jar).get().use { zip ->
                            val entry =
                                zip.getEntry("AndroidManifest.xml")
                                    ?: throw IllegalStateException("Jar has no AndroidManifest.xml")
                            zip.getInputStream(entry).use { AndroidManifestParser.parse(it) }
                        }
                    Package.Jar(jar, manifest)
                } else {
                    val apk = download(info.apkUrl, "$pkgName.apk")
                    Package.Apk(apk, PackageTools.getPackageInfo(apk))
                }

            try {
                val meta = pkg.metadata()
                if (meta.packageName != pkgName) {
                    throw IllegalStateException("Package name mismatch: expected $pkgName, got ${meta.packageName}")
                }
                if (!meta.reqFeatures.contains(EXTENSION_FEATURE)) {
                    throw IllegalStateException("This file is not a Mihon/Tachiyomi extension")
                }
                val libVersion = meta.versionName.substringBeforeLast('.').toDouble()
                if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
                    throw IllegalStateException(
                        "Extension lib version is $libVersion, only $LIB_VERSION_MIN to $LIB_VERSION_MAX are supported",
                    )
                }

                val sourceClass =
                    meta.getString(METADATA_SOURCE_CLASS)?.trim()
                        ?: throw IllegalStateException("Extension does not declare $METADATA_SOURCE_CLASS")
                // A factory may list several classes separated by ';' — Mihon loads each one
                val classNames =
                    sourceClass.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map {
                        if (it.startsWith(".")) pkgName + it else it
                    }

                val nsfw =
                    listOf(meta.getString(METADATA_CONTENT_WARNING), meta.getString(METADATA_NSFW))
                        .any { (it?.toIntOrNull() ?: 0) > 0 } || info.nsfw

                val jarName = "$pkgName-v${meta.versionName}.jar"
                val jarFile = extensionsRoot / jarName

                // Build the final jar
                jarFile.deleteIfExists()
                pkg.writeJar(jarFile)

                // Unload the previous version before loading the new one
                existing?.let { SourceManager.unloadExtension(it, jarPath(it)) }

                val sources =
                    try {
                        classNames.flatMap { className ->
                            when (val instance = PackageTools.loadExtensionSources(jarFile, className)) {
                                is Source -> listOf(instance)
                                is SourceFactory -> instance.createSources()
                                else -> throw IllegalStateException("Unknown source class type ${instance.javaClass}")
                            }
                        }
                    } catch (e: Throwable) {
                        PackageTools.jarLoaderMap.remove(jarFile.absolutePathString())?.close()
                        jarFile.deleteIfExists()
                        throw e
                    }

                val langs = sources.map { it.lang }.toSet()
                val ext =
                    InstalledExtension(
                        pkgName = pkgName,
                        name = meta.getString(METADATA_NAME) ?: meta.label?.substringAfter("Tachiyomi: ") ?: info.name,
                        versionName = meta.versionName,
                        versionCode = meta.versionCode.toLong(),
                        lang =
                            when (langs.size) {
                                0 -> info.lang
                                1 -> langs.first()
                                else -> "all"
                            },
                        nsfw = nsfw,
                        className = classNames.joinToString(";"),
                        jarName = jarName,
                        iconUrl = info.iconUrl.ifBlank { null },
                        sources = sources.map { RepoSource(id = it.id.toString(), name = it.name, lang = it.lang) },
                    )

                SourceManager.register(pkgName, sources)
                installed = installed + (pkgName to ext)
                save()

                if (existing != null && existing.jarName != jarName) {
                    runCatching { jarPath(existing).deleteIfExists() }
                }
                logger.info { "Installed ${ext.name} ${ext.versionName} with ${sources.size} source(s)" }
                ext
            } finally {
                pkg.file.deleteIfExists()
            }
        }

    suspend fun uninstall(pkgName: String) =
        mutex.withLock {
            val ext = installed[pkgName] ?: return@withLock
            SourceManager.unloadExtension(ext, jarPath(ext))
            installed = installed - pkgName
            save()
            runCatching { jarPath(ext).deleteIfExists() }
                .onFailure { logger.warn(it) { "Could not delete ${ext.jarName}" } }
        }

    /** Installed extensions whose repo version is newer */
    suspend fun updatable(): Map<String, RepoExtension> {
        val repo = ExtensionRepo.list().associateBy { it.pkgName }
        return installed.values
            .mapNotNull { ext -> repo[ext.pkgName]?.takeIf { it.versionCode > ext.versionCode } }
            .associateBy { it.pkgName }
    }

    private suspend fun download(
        url: String,
        name: String,
    ): Path {
        val target = tempRoot / name
        target.createParentDirectories()
        val response = network.client.newCall(GET(url, cache = CacheControl.FORCE_NETWORK)).awaitSuccess()
        response.body.byteStream().use { input ->
            target.outputStream().buffered().use { input.copyTo(it) }
        }
        return target
    }

    data class Metadata(
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val reqFeatures: List<String>,
        val label: String?,
        private val values: Map<String, String?>,
    ) {
        fun getString(key: String): String? = values[key]
    }

    private sealed class Package {
        abstract val file: Path

        abstract fun metadata(): Metadata

        abstract fun writeJar(target: Path)

        class Apk(
            override val file: Path,
            val packageInfo: android.content.pm.PackageInfo,
        ) : Package() {
            override fun metadata(): Metadata {
                val bundle = packageInfo.applicationInfo.metaData
                return Metadata(
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName,
                    versionCode = packageInfo.versionCode,
                    reqFeatures = packageInfo.reqFeatures.orEmpty().map { it.name },
                    label = packageInfo.applicationInfo.nonLocalizedLabel?.toString(),
                    values = bundle.keySet().associateWith { key -> bundle.get(key)?.toString() },
                )
            }

            override fun writeJar(target: Path) {
                PackageTools.dex2jar(file, target)
                check(target.exists()) { "dex2jar did not produce a jar" }
                mergeApkAssets(file, target)
            }
        }

        class Jar(
            override val file: Path,
            val manifest: AndroidManifestParser.AndroidManifest,
        ) : Package() {
            override fun metadata(): Metadata =
                Metadata(
                    packageName = manifest.packageName,
                    versionName = manifest.versionName ?: throw IllegalStateException("Manifest has no versionName"),
                    versionCode = manifest.versionCode ?: throw IllegalStateException("Manifest has no versionCode"),
                    reqFeatures = manifest.usesFeatures.mapNotNull { it.name },
                    label = manifest.application?.label,
                    values = manifest.application?.metaData.orEmpty().associate { it.name to it.value },
                )

            override fun writeJar(target: Path) {
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    /** Extensions read bundled files (e.g. assets/) through the class loader; copy them into the jar. */
    private fun mergeApkAssets(
        apk: Path,
        jar: Path,
    ) {
        val assets = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(apk.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("assets/") && !entry.isDirectory) {
                    assets += entry.name.removePrefix("assets/") to zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        if (assets.isEmpty()) return

        val tmp = jar.resolveSibling(jar.fileName.toString() + ".tmp")
        ZipInputStream(jar.inputStream()).use { input ->
            ZipOutputStream(tmp.outputStream()).use { output ->
                val written = mutableSetOf<String>()
                var entry = input.nextEntry
                while (entry != null) {
                    if (!entry.name.startsWith("META-INF/") && written.add(entry.name)) {
                        output.putNextEntry(ZipEntry(entry.name))
                        input.copyTo(output)
                        output.closeEntry()
                    }
                    entry = input.nextEntry
                }
                assets.forEach { (name, bytes) ->
                    if (written.add(name)) {
                        output.putNextEntry(ZipEntry(name))
                        output.write(bytes)
                        output.closeEntry()
                    }
                }
            }
        }
        tmp.moveTo(jar, overwrite = true)
    }
}
