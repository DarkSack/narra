package app.narra.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.narra.domain.model.AudioFormat
import app.narra.domain.model.AudioQuality
import app.narra.domain.model.BookState
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.JobState
import app.narra.domain.model.SegmentState

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val isbn: String? = null,
    /** Palabras clave separadas por salto de línea. */
    val keywords: String? = null,
    val coverPath: String? = null,
    val sourceFileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int = 0,
    val importedAt: Long,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false,
    val state: BookState,
    val errorKind: ErrorKind? = null,
    val errorDetail: String? = null,
    // Análisis
    val pagesAnalyzed: Int = 0,
    val scannedPages: Int = 0,
    val ocrPagesDone: Int = 0,
    val ocrPagesTotal: Int = 0,
    /** [app.narra.domain.model.AnalysisReport] serializado. */
    val analysisJson: String? = null,
    // Voz y audio
    val voiceProviderId: String,
    val voiceId: String? = null,
    val voiceLanguage: String? = null,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val quality: AudioQuality = AudioQuality.STANDARD,
    val format: AudioFormat = AudioFormat.M4A,
    val paragraphPauseMs: Int,
    val chapterPauseMs: Int,
    // Contadores derivados, recalculados en cada cambio de capítulos o segmentos.
    val chapterCount: Int = 0,
    val chaptersAvailable: Int = 0,
    val segmentCount: Int = 0,
    val segmentsAvailable: Int = 0,
    val totalChars: Int = 0,
    val durationMs: Long = 0,
    val generatedDurationMs: Long = 0,
    val audioSizeBytes: Long = 0,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId", "orderIndex")],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val orderIndex: Int,
    val title: String,
    val level: Int = 0,
    val startPage: Int,
    val endPage: Int,
    val included: Boolean = true,
    val state: ChapterState = ChapterState.PENDING,
    val charCount: Int = 0,
    val segmentCount: Int = 0,
    val segmentsAvailable: Int = 0,
    val durationMs: Long = 0,
    val errorKind: ErrorKind? = null,
    val errorDetail: String? = null,
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("chapterId", "orderIndex"), Index("bookId", "state")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterId: Long,
    val orderIndex: Int,
    /** Lista de [app.narra.domain.model.Paragraph] serializada. */
    val paragraphsJson: String,
    val charCount: Int,
    val state: SegmentState = SegmentState.PENDING,
    val attempts: Int = 0,
    val errorKind: ErrorKind? = null,
    val errorDetail: String? = null,
)

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(entity = SegmentEntity::class, parentColumns = ["id"], childColumns = ["segmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId"), Index("chapterId")],
)
data class AudioTrackEntity(
    @PrimaryKey val segmentId: Long,
    val bookId: String,
    val chapterId: Long,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: AudioFormat,
    val createdAt: Long,
)

@Entity(
    tableName = "playback_progress",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaybackProgressEntity(
    @PrimaryKey val bookId: String,
    val segmentId: Long?,
    val chapterId: Long?,
    val chapterTitle: String?,
    val positionMs: Long,
    val listenedMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "processing_jobs",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ProcessingJobEntity(
    @PrimaryKey val bookId: String,
    val state: JobState,
    val enqueuedAt: Long,
    val attempts: Int = 0,
    val priorityChapterId: Long? = null,
    val currentChapterId: Long? = null,
    val currentSegmentIndex: Int = 0,
    val throughputCharsPerSecond: Float = 0f,
    val errorKind: ErrorKind? = null,
    val errorDetail: String? = null,
)

/** Libro con su progreso de escucha (relación 1 a 0..1). */
data class BookWithProgress(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId")
    val progress: PlaybackProgressEntity?,
)

/** Fila de la cola de reproducción: segmento generado con datos de su capítulo. */
data class PlayableTrackRow(
    val segmentId: Long,
    val chapterId: Long,
    @ColumnInfo(name = "chapterOrder") val chapterOrder: Int,
    val chapterTitle: String,
    val segmentOrder: Int,
    val path: String,
    val durationMs: Long,
)

/** Totales de un capítulo o libro calculados en SQL. */
data class SegmentTotals(
    val segmentCount: Int,
    val segmentsAvailable: Int,
    val totalChars: Int,
    val generatedChars: Int,
    val generatedDurationMs: Long,
    val audioSizeBytes: Long,
)
