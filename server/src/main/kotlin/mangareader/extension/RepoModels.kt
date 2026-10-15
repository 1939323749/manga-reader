package mangareader.extension

/*
 * Wire formats of Mihon extension repositories.
 * Adapted from Suwayomi-Server (MPL-2.0):
 *   suwayomi/tachidesk/manga/impl/extension/github/ (package)
 */

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.protobuf.ProtoNumber

/** repo.json (legacy, may point at an index_v2) */
@Serializable
data class LegacyRepoJson(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: Meta,
) {
    @Serializable
    data class Meta(
        val name: String,
        val shortName: String? = null,
        val website: String = "",
        val signingKeyFingerprint: String = "",
    )
}

/** entries of index.min.json (legacy) */
@Serializable
data class LegacyIndexEntry(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val version: String,
    val code: Long,
    val nsfw: Int,
    val sources: List<Source>? = null,
) {
    @Serializable
    data class Source(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String = "",
    )
}

/** index.pb / index v2 (protobuf or json) */
@Serializable
data class StoreIndex(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(
        @ProtoNumber(1) val extensions: List<Extension> = emptyList(),
    )

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String = "",
        // Keiyoushi specific: prebuilt JVM jar
        @ProtoNumber(501) val jarUrl: String? = null,
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(7) val message: String? = null,
    )

    @Serializable
    enum class ContentWarning {
        @ProtoNumber(0)
        @JsonNames("CONTENT_WARNING_UNSPECIFIED")
        UNSPECIFIED,

        @ProtoNumber(1)
        @JsonNames("CONTENT_WARNING_SAFE")
        SAFE,

        @ProtoNumber(2)
        @JsonNames("CONTENT_WARNING_MIXED")
        MIXED,

        @ProtoNumber(3)
        @JsonNames("CONTENT_WARNING_NSFW")
        NSFW,
    }
}

/** What the rest of the app works with. Source ids are strings: they don't fit in a JS number. */
@Serializable
data class RepoExtension(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val nsfw: Boolean,
    val apkUrl: String,
    val jarUrl: String?,
    val iconUrl: String,
    val repo: String,
    val sources: List<RepoSource>,
)

@Serializable
data class RepoSource(
    val id: String,
    val name: String,
    val lang: String,
)
