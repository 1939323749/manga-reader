package mangareader.api

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.Serializable
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.TlsVersion
import org.conscrypt.Conscrypt
import uy.kohesive.injekt.injectLazy
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Fetches one URL several different ways so connection problems (proxy, TLS fingerprint,
 * protocol) can be told apart without guessing. Only reachable from this machine.
 */
object NetDiagnostics {
    private val network: NetworkHelper by injectLazy()

    @Serializable
    data class Attempt(
        val name: String,
        val ok: Boolean,
        val status: Int? = null,
        val millis: Long,
        val protocol: String? = null,
        val tls: String? = null,
        val error: String? = null,
    )

    @Serializable
    data class Report(
        val url: String,
        val proxy: String,
        val attempts: List<Attempt>,
    )

    /**
     * The JDK's trust manager with Conscrypt's TLS 1.3 auth type ("GENERIC") mapped to the
     * name the JDK uses for TLS 1.3 ("UNKNOWN"); otherwise every TLS 1.3 handshake fails.
     */
    val conscryptTrust: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val jdk = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        object : X509TrustManager {
            private fun map(authType: String?) = if (authType == "GENERIC") "UNKNOWN" else authType

            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) = jdk.checkClientTrusted(chain, map(authType))

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) = jdk.checkServerTrusted(chain, map(authType))

            override fun getAcceptedIssuers(): Array<X509Certificate> = jdk.acceptedIssuers
        }
    }

    /** OkHttp client using Conscrypt (BoringSSL), i.e. the same TLS stack as Mihon on Android */
    fun conscryptBuilder(base: OkHttpClient.Builder): OkHttpClient.Builder {
        val ctx = SSLContext.getInstance("TLS", Conscrypt.newProvider())
        ctx.init(null, arrayOf(conscryptTrust), null)
        return base.sslSocketFactory(ctx.socketFactory, conscryptTrust)
    }

    fun run(url: String): Report {
        // a fresh builder each time: OkHttpClient.Builder is mutable
        fun bare() =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
        val ua = network.defaultUserAgentProvider()

        val variants: List<Pair<String, () -> OkHttpClient>> =
            listOf(
                "app client (JDK TLS)" to { network.client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build() },
                "JDK TLS, HTTP/1.1" to { bare().protocols(listOf(Protocol.HTTP_1_1)).build() },
                "JDK TLS 1.2 only" to {
                    bare()
                        .connectionSpecs(
                            listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_2).build()),
                        ).build()
                },
                "Conscrypt TLS" to { conscryptBuilder(bare()).build() },
                "JDK TLS, no proxy" to { bare().proxy(Proxy.NO_PROXY).build() },
                "Conscrypt TLS, no proxy" to {
                    conscryptBuilder(bare())
                        .proxy(Proxy.NO_PROXY)
                        .build()
                },
            )

        val attempts =
            variants.map { (name, make) ->
                val start = System.currentTimeMillis()
                try {
                    val client = make()
                    client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { res ->
                        Attempt(
                            name = name,
                            ok = res.isSuccessful,
                            status = res.code,
                            millis = System.currentTimeMillis() - start,
                            protocol = res.protocol.toString(),
                            tls = res.handshake?.let { "${it.tlsVersion.javaName} ${it.cipherSuite.javaName}" },
                        )
                    }
                } catch (e: Throwable) {
                    val chain =
                        generateSequence(e) { it.cause }
                            .take(4)
                            .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
                    Attempt(name = name, ok = false, millis = System.currentTimeMillis() - start, error = chain)
                }
            }

        val proxy =
            runCatching { ProxySelector.getDefault().select(URI(url)).joinToString() }
                .getOrElse { "unknown (${it.message})" }
        return Report(url, proxy, attempts)
    }
}
