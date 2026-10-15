package mangareader

import java.io.File

/**
 * All runtime configuration, read from environment variables (or -D system properties
 * with the same name in lower-case dotted form, e.g. -Dmanga.reader.port=4567).
 */
object AppConfig {
    private fun read(
        env: String,
        default: String,
    ): String {
        val prop = env.lowercase().replace('_', '.')
        return System.getProperty(prop) ?: System.getenv(env)?.takeIf { it.isNotBlank() } ?: default
    }

    val host: String = read("MANGA_READER_HOST", "0.0.0.0")
    val port: Int = read("MANGA_READER_PORT", "4567").toInt()

    val dataRoot: String =
        File(read("MANGA_READER_DATA", File(System.getProperty("user.home"), ".manga-reader").path))
            .absolutePath

    /** Extension repositories (repo.json / index.min.json / index.pb URLs), comma separated */
    val extensionRepos: List<String> =
        read(
            "MANGA_READER_REPOS",
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json",
        ).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Show extensions marked NSFW in the repo */
    val showNsfw: Boolean = read("MANGA_READER_SHOW_NSFW", "false").toBoolean()

    // FlareSolverr (optional Cloudflare bypass)
    val flareSolverrEnabled: Boolean = read("FLARESOLVERR_ENABLED", "false").toBoolean()
    val flareSolverrUrl: String = read("FLARESOLVERR_URL", "http://localhost:8191")
    val flareSolverrTimeout: Int = read("FLARESOLVERR_TIMEOUT", "60").toInt()
    val flareSolverrSessionName: String = read("FLARESOLVERR_SESSION_NAME", "manga-reader")
    val flareSolverrSessionTtl: Int = read("FLARESOLVERR_SESSION_TTL", "15").toInt()
    val flareSolverrAsResponseFallback: Boolean = read("FLARESOLVERR_AS_RESPONSE_FALLBACK", "false").toBoolean()

    /**
     * Outgoing proxy for all source requests, e.g. http://127.0.0.1:7890 or socks5://127.0.0.1:7891.
     * Empty = use the operating system's proxy settings; "none" = connect directly.
     */
    val proxy: String = read("MANGA_READER_PROXY", "")

    /** Optional SOCKS proxy for FlareSolverr, e.g. socks5://127.0.0.1:1080 */
    val socksProxyUrl: String? = read("MANGA_READER_SOCKS_PROXY", "").ifBlank { null }
}
