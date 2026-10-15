package suwayomi.tachidesk.server

/*
 * Small stand-ins for Suwayomi server internals that the copied
 * eu.kanade.tachiyomi / util code still references.
 */

import kotlinx.coroutines.flow.MutableStateFlow
import mangareader.AppConfig

class ApplicationDirs(
    val dataRoot: String = AppConfig.dataRoot,
    val tempRoot: String = "${System.getProperty("java.io.tmpdir")}/manga-reader",
) {
    val extensionsRoot = "$dataRoot/extensions"
    val cacheDir = "$dataRoot/cache"
}

/** Read-only replacement for Suwayomi's ServerConfig, only the fields CloudflareInterceptor uses. */
object ServerConfigShim {
    val flareSolverrEnabled = MutableStateFlow(AppConfig.flareSolverrEnabled)
    val flareSolverrUrl = MutableStateFlow(AppConfig.flareSolverrUrl)
    val flareSolverrTimeout = MutableStateFlow(AppConfig.flareSolverrTimeout)
    val flareSolverrSessionName = MutableStateFlow(AppConfig.flareSolverrSessionName)
    val flareSolverrSessionTtl = MutableStateFlow(AppConfig.flareSolverrSessionTtl)
    val flareSolverrAsResponseFallback = MutableStateFlow(AppConfig.flareSolverrAsResponseFallback)
}

val serverConfig: ServerConfigShim
    get() = ServerConfigShim
