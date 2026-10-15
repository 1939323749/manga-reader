package mangareader

import kotlinx.serialization.json.Json

object JsonCodec {
    val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
            encodeDefaults = true
        }

    val pretty =
        Json(json) {
            prettyPrint = true
        }
}
