package mangareader

/*
 * Startup sequence adapted from Suwayomi-Server's ServerSetup.kt (MPL-2.0):
 * Android main looper -> Koin modules -> AndroidCompat -> Application -> HTTP server.
 */

import android.os.Looper
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.createAppModule
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mangareader.api.Api.defineApi
import mangareader.api.Api.defineErrors
import org.eclipse.jetty.server.ServerConnector
import org.koin.core.context.startKoin
import org.koin.dsl.module
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.ts.config.CONFIG_PREFIX
import xyz.nulldev.ts.config.configManagerModule
import java.io.File
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

@Suppress("DEPRECATION")
private class LooperThread : Thread("android-main-looper") {
    init {
        isDaemon = true
    }

    override fun run() {
        Looper.prepareMainLooper()
        Looper.loop()
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun setupAndroid() {
    // The config module and AndroidCompat read their root dir from this property
    System.setProperty("$CONFIG_PREFIX.server.rootDir", AppConfig.dataRoot)

    val dirs = ApplicationDirs()
    listOf(dirs.dataRoot, dirs.extensionsRoot, dirs.cacheDir, dirs.tempRoot).forEach { File(it).mkdirs() }

    LooperThread().start()

    val app = App()
    startKoin {
        modules(
            createAppModule(app),
            androidCompatModule(),
            configManagerModule(),
            module { single { dirs } },
        )
    }

    AndroidCompatInitializer().init()
    AndroidCompat().startApp(app)

    Injekt
        .get<NetworkHelper>()
        .userAgentFlow
        .onEach { System.setProperty("http.agent", it) }
        .launchIn(GlobalScope)
}

fun startServer(): Javalin {
    val hasWebUi = AppConfig::class.java.getResource("/web/index.html") != null

    val app =
        Javalin.start { config ->
            config.jetty.modifyServer { server ->
                if (server.connectors.none { it is ServerConnector && it.port == AppConfig.port }) {
                    server.addConnector(
                        ServerConnector(server).apply {
                            host = AppConfig.host
                            port = AppConfig.port
                        },
                    )
                }
            }

            config.bundledPlugins.enableCors { cors ->
                cors.addRule {
                    it.path = "/api/*"
                    it.allowCredentials = false
                    it.reflectClientOrigin = true
                }
            }

            if (hasWebUi) {
                config.staticFiles.add { staticFiles ->
                    staticFiles.directory = "/web"
                    staticFiles.location = Location.CLASSPATH
                }
                config.spaRoot.addFile("/", "/web/index.html", Location.CLASSPATH)
            }

            config.routes.defineApi()
            config.routes.defineErrors()

            config.events.serverStarted {
                logger.info { "manga-reader listening on http://${AppConfig.host}:${AppConfig.port}" }
                logger.info { "data directory: ${AppConfig.dataRoot}" }
                if (!hasWebUi) logger.warn { "No bundled web UI found; only the API is served" }
            }
        }

    Runtime.getRuntime().addShutdownHook(thread(start = false) { app.stop() })
    return app
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        logger.error(throwable) { "unhandled exception" }
    }
    setupAndroid()
    try {
        startServer()
    } catch (e: io.javalin.util.JavalinBindException) {
        logger.error {
            "Port ${AppConfig.port} is already in use (another manga-reader or Suwayomi may be running).\n" +
                "Start on a different port, e.g.:  java \"-Dmanga.reader.port=8080\" -jar manga-reader.jar\n" +
                "or set the MANGA_READER_PORT environment variable."
        }
        kotlin.system.exitProcess(1)
    }
}
