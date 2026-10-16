package mangareader.api

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/*
 * The API is stateless: the client sends back the manga / chapter objects it received,
 * so the server never has to store them. `memo` is opaque per-source data and must be
 * round-tripped untouched.
 */

@Serializable
data class MangaDto(
    val url: String,
    val title: String = "",
    val thumbnailUrl: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = SManga.UNKNOWN,
    val initialized: Boolean = false,
    val memo: JsonObject? = null,
)

@Serializable
data class ChapterDto(
    val url: String,
    val name: String = "",
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    val dateUpload: Long = 0,
    val memo: JsonObject? = null,
)

@Serializable
data class PageDto(
    val index: Int,
    val url: String,
    val imageUrl: String?,
)

@Serializable
data class MangasPageDto(
    val mangas: List<MangaDto>,
    val hasNextPage: Boolean,
)

@Serializable
data class MangaWithChaptersDto(
    val manga: MangaDto,
    val chapters: List<ChapterDto>,
)

@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val lang: String,
    val pkgName: String,
    val extensionName: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val supportsLatest: Boolean? = null,
)

@Serializable
data class RepoExtensionDto(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val lang: String,
    val nsfw: Boolean,
    val iconUrl: String,
    val sourceCount: Int,
    val installed: Boolean,
    val installedVersion: String?,
    val hasUpdate: Boolean,
)

@Serializable
data class ErrorDto(
    val error: String,
)

fun SManga.toDto(): MangaDto =
    MangaDto(
        // details parsers usually don't set url (the host app is expected to keep it)
        url = runCatching { url }.getOrDefault(""),
        // Extensions sometimes leave lateinit fields unset on partial objects
        title = runCatching { title }.getOrDefault(""),
        thumbnailUrl = thumbnail_url,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        initialized = initialized,
        memo = runCatching { memo }.getOrNull()?.takeIf { it.isNotEmpty() },
    )

fun MangaDto.toSManga(): SManga =
    SManga.create().also {
        it.url = url
        it.title = title
        it.thumbnail_url = thumbnailUrl
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.initialized = initialized
        memo?.let { m -> it.memo = m }
    }

fun SChapter.toDto(): ChapterDto =
    ChapterDto(
        url = runCatching { url }.getOrDefault(""),
        name = runCatching { name }.getOrDefault(""),
        chapterNumber = chapter_number,
        scanlator = scanlator,
        dateUpload = date_upload,
        memo = runCatching { memo }.getOrNull()?.takeIf { it.isNotEmpty() },
    )

fun ChapterDto.toSChapter(): SChapter =
    SChapter.create().also {
        it.url = url
        it.name = name
        it.chapter_number = chapterNumber
        it.scanlator = scanlator
        it.date_upload = dateUpload
        memo?.let { m -> it.memo = m }
    }

fun MangasPage.toDto(): MangasPageDto = MangasPageDto(mangas.map { it.toDto() }, hasNextPage)

fun Page.toDto(): PageDto = PageDto(index, url, imageUrl)

/**
 * One entry of a source's filter list. `type` is one of header, separator, text, checkbox,
 * tristate, select, sort, group, unknown. `state` depends on the type: string (text),
 * boolean (checkbox), 0/1/2 = ignore/include/exclude (tristate), option index (select),
 * {index, ascending} or null (sort). Groups carry their children in `filters`.
 */
@Serializable
data class FilterDto(
    val index: Int,
    val type: String,
    val name: String,
    val state: JsonElement = JsonNull,
    val values: List<String>? = null,
    val filters: List<FilterDto>? = null,
)

/** New state for the filter at `index` (or for child `childIndex` of the group at `index`). */
@Serializable
data class FilterChangeDto(
    val index: Int,
    val childIndex: Int? = null,
    val state: JsonElement = JsonNull,
)

@Serializable
data class SearchRequestDto(
    val q: String = "",
    val page: Int = 1,
    val filters: List<FilterChangeDto> = emptyList(),
)

/** `type`: list, multiselect, text, switch, checkbox, other (shown read-only). */
@Serializable
data class PreferenceDto(
    val key: String?,
    val type: String,
    val title: String?,
    val summary: String?,
    val enabled: Boolean,
    val visible: Boolean,
    val entries: List<String>?,
    val entryValues: List<String>?,
    val dialogTitle: String?,
    val dialogMessage: String?,
    val value: JsonElement,
    val defaultValue: JsonElement,
)

@Serializable
data class SourcePreferencesDto(
    val configurable: Boolean,
    val preferences: List<PreferenceDto>,
)

@Serializable
data class PreferenceChangeDto(
    val key: String,
    val value: JsonElement = JsonNull,
)
