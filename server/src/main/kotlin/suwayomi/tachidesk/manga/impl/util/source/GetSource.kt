package suwayomi.tachidesk.manga.impl.util.source

import mangareader.source.SourceManager

/** Compatibility shim: NetworkHelper resets cached sources when the user agent changes. */
object GetSource {
    fun unregisterAllSources() = SourceManager.clear()
}
