package mangareader.api

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
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
        url = url,
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
        url = url,
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
