package app.narra.data.db

import app.narra.domain.model.AnalysisProgress
import app.narra.domain.model.AnalysisReport
import app.narra.domain.model.Book
import app.narra.domain.model.BookMetadata
import app.narra.domain.model.Chapter
import app.narra.domain.model.ListeningProgress
import app.narra.domain.model.Paragraph
import app.narra.domain.model.PlayableTrack
import app.narra.domain.model.ProcessingError
import app.narra.domain.model.ProcessingJob
import app.narra.domain.model.Segment
import app.narra.domain.model.VoiceSettings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal val NarraJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

private val paragraphListSerializer = ListSerializer(Paragraph.serializer())

internal fun List<Paragraph>.toJson(): String = NarraJson.encodeToString(paragraphListSerializer, this)

internal fun String.toParagraphs(): List<Paragraph> = NarraJson.decodeFromString(paragraphListSerializer, this)

internal fun AnalysisReport.toJson(): String = NarraJson.encodeToString(AnalysisReport.serializer(), this)

internal fun String.toAnalysisReport(): AnalysisReport =
    runCatching { NarraJson.decodeFromString(AnalysisReport.serializer(), this) }.getOrDefault(AnalysisReport())

private fun error(kind: app.narra.domain.model.ErrorKind?, detail: String?): ProcessingError? =
    kind?.let { ProcessingError(it, detail) }

internal fun BookEntity.metadata() = BookMetadata(
    title = title,
    author = author,
    description = description,
    language = language,
    publisher = publisher,
    publishedDate = publishedDate,
    isbn = isbn,
    keywords = keywords?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
)

internal fun BookEntity.voiceSettings() = VoiceSettings(
    providerId = voiceProviderId,
    voiceId = voiceId,
    languageTag = voiceLanguage,
    speechRate = speechRate,
    pitch = pitch,
    quality = quality,
    format = format,
    paragraphPauseMs = paragraphPauseMs,
    chapterPauseMs = chapterPauseMs,
)

internal fun BookWithProgress.toDomain(): Book = Book(
    id = book.id,
    metadata = book.metadata(),
    coverPath = book.coverPath,
    sourceFileName = book.sourceFileName,
    fileSizeBytes = book.fileSizeBytes,
    pageCount = book.pageCount,
    importedAt = book.importedAt,
    lastPlayedAt = book.lastPlayedAt,
    isFavorite = book.isFavorite,
    state = book.state,
    error = error(book.errorKind, book.errorDetail),
    voice = book.voiceSettings(),
    chapterCount = book.chapterCount,
    chaptersAvailable = book.chaptersAvailable,
    segmentCount = book.segmentCount,
    segmentsAvailable = book.segmentsAvailable,
    totalChars = book.totalChars,
    durationMs = book.durationMs,
    generatedDurationMs = book.generatedDurationMs,
    audioSizeBytes = book.audioSizeBytes,
    analysis = AnalysisProgress(
        pagesDone = book.pagesAnalyzed,
        pagesTotal = book.pageCount,
        scannedPages = book.scannedPages,
        ocrPagesDone = book.ocrPagesDone,
        ocrPagesTotal = book.ocrPagesTotal,
    ),
    progress = progress?.toDomain(),
)

internal fun PlaybackProgressEntity.toDomain() = ListeningProgress(
    bookId = bookId,
    segmentId = segmentId,
    chapterId = chapterId,
    chapterTitle = chapterTitle,
    positionMs = positionMs,
    listenedMs = listenedMs,
    completed = completed,
    updatedAt = updatedAt,
)

internal fun ChapterEntity.toDomain() = Chapter(
    id = id,
    bookId = bookId,
    index = orderIndex,
    title = title,
    level = level,
    startPage = startPage,
    endPage = endPage,
    included = included,
    state = state,
    charCount = charCount,
    segmentCount = segmentCount,
    segmentsAvailable = segmentsAvailable,
    durationMs = durationMs,
    error = error(errorKind, errorDetail),
)

internal fun SegmentEntity.toDomain() = Segment(
    id = id,
    bookId = bookId,
    chapterId = chapterId,
    index = orderIndex,
    paragraphs = paragraphsJson.toParagraphs(),
    state = state,
    attempts = attempts,
    error = error(errorKind, errorDetail),
)

internal fun PlayableTrackRow.toDomain() = PlayableTrack(
    segmentId = segmentId,
    chapterId = chapterId,
    chapterIndex = chapterOrder,
    chapterTitle = chapterTitle,
    segmentIndex = segmentOrder,
    path = path,
    durationMs = durationMs,
)

internal fun ProcessingJobEntity.toDomain() = ProcessingJob(
    bookId = bookId,
    state = state,
    enqueuedAt = enqueuedAt,
    attempts = attempts,
    priorityChapterId = priorityChapterId,
    currentChapterId = currentChapterId,
    currentSegmentIndex = currentSegmentIndex,
    throughputCharsPerSecond = throughputCharsPerSecond,
    error = error(errorKind, errorDetail),
)
