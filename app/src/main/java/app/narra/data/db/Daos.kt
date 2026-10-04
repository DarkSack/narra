package app.narra.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.narra.domain.model.BookState
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.JobState
import app.narra.domain.model.SegmentState
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookWithProgress>>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookWithProgress?>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getWithProgress(id: String): BookWithProgress?

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT id FROM books WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    fun observeLastPlayedId(): Flow<String?>

    @Query("SELECT id FROM books WHERE state IN (:states)")
    suspend fun idsInStates(states: List<BookState>): List<String>

    @Query("SELECT * FROM books WHERE sourceFileName = :fileName AND fileSizeBytes = :size LIMIT 1")
    suspend fun findBySource(fileName: String, size: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET state = :state, errorKind = :errorKind, errorDetail = :errorDetail WHERE id = :id")
    suspend fun setState(id: String, state: BookState, errorKind: ErrorKind? = null, errorDetail: String? = null)

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE books SET lastPlayedAt = :at WHERE id = :id")
    suspend fun touchPlayed(id: String, at: Long)

    @Query("UPDATE books SET pagesAnalyzed = :done, pageCount = :total, scannedPages = :scanned WHERE id = :id")
    suspend fun setAnalysisProgress(id: String, done: Int, total: Int, scanned: Int)

    @Query("UPDATE books SET ocrPagesDone = :done, ocrPagesTotal = :total WHERE id = :id")
    suspend fun setOcrProgress(id: String, done: Int, total: Int)

    @Query("UPDATE books SET coverPath = :path WHERE id = :id")
    suspend fun setCover(id: String, path: String?)

    @Query(
        """UPDATE books SET chapterCount = :chapterCount, chaptersAvailable = :chaptersAvailable,
           segmentCount = :segmentCount, segmentsAvailable = :segmentsAvailable, totalChars = :totalChars,
           durationMs = :durationMs, generatedDurationMs = :generatedDurationMs, audioSizeBytes = :audioSizeBytes
           WHERE id = :id""",
    )
    suspend fun setStats(
        id: String,
        chapterCount: Int,
        chaptersAvailable: Int,
        segmentCount: Int,
        segmentsAvailable: Int,
        totalChars: Int,
        durationMs: Long,
        generatedDurationMs: Long,
        audioSizeBytes: Long,
    )
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex")
    fun observe(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex")
    suspend fun getAll(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: Long): ChapterEntity?

    @Insert
    suspend fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun updateAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT DISTINCT bookId FROM chapters WHERE title LIKE :pattern")
    suspend fun bookIdsWithTitleLike(pattern: String): List<String>

    @Query("UPDATE chapters SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE chapters SET included = :included WHERE id = :id")
    suspend fun setIncluded(id: Long, included: Boolean)

    @Query("UPDATE chapters SET state = :state, errorKind = :errorKind, errorDetail = :errorDetail WHERE id = :id")
    suspend fun setState(id: Long, state: ChapterState, errorKind: ErrorKind? = null, errorDetail: String? = null)

    @Query("UPDATE chapters SET state = 'PENDING', errorKind = NULL, errorDetail = NULL WHERE bookId = :bookId AND state = 'ERROR'")
    suspend fun resetErrors(bookId: String)

    @Query(
        """UPDATE chapters SET segmentCount = :segmentCount, segmentsAvailable = :segmentsAvailable,
           charCount = :charCount, durationMs = :durationMs WHERE id = :id""",
    )
    suspend fun setStats(id: Long, segmentCount: Int, segmentsAvailable: Int, charCount: Int, durationMs: Long)

    @Query(
        """SELECT COUNT(*) AS segmentCount,
           COALESCE(SUM(CASE WHEN s.state = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS segmentsAvailable,
           COALESCE(SUM(s.charCount), 0) AS totalChars,
           COALESCE(SUM(CASE WHEN s.state = 'AVAILABLE' THEN s.charCount ELSE 0 END), 0) AS generatedChars,
           COALESCE(SUM(t.durationMs), 0) AS generatedDurationMs,
           COALESCE(SUM(t.sizeBytes), 0) AS audioSizeBytes
           FROM segments s LEFT JOIN audio_tracks t ON t.segmentId = s.id
           WHERE s.chapterId = :chapterId""",
    )
    suspend fun totals(chapterId: Long): SegmentTotals
}

@Dao
interface SegmentDao {
    @Insert
    suspend fun insertAll(segments: List<SegmentEntity>)

    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun get(id: Long): SegmentEntity?

    @Query("SELECT * FROM segments WHERE chapterId = :chapterId ORDER BY orderIndex")
    suspend fun forChapter(chapterId: Long): List<SegmentEntity>

    /**
     * Siguiente segmento por generar: primero desde el capítulo [fromChapterOrder] en adelante
     * (el que el usuario quiere escuchar), después los anteriores. Los PROCESSING huérfanos de un
     * proceso que murió se recogen igual.
     */
    @Query(
        """SELECT s.* FROM segments s JOIN chapters c ON c.id = s.chapterId
           WHERE s.bookId = :bookId AND c.included = 1
             AND s.state IN ('PENDING', 'PROCESSING')
           ORDER BY CASE WHEN c.orderIndex >= :fromChapterOrder THEN 0 ELSE 1 END, c.orderIndex, s.orderIndex
           LIMIT 1""",
    )
    suspend fun nextToGenerate(bookId: String, fromChapterOrder: Int): SegmentEntity?

    @Query(
        """SELECT COUNT(*) FROM segments s JOIN chapters c ON c.id = s.chapterId
           WHERE s.bookId = :bookId AND c.included = 1 AND s.state = 'ERROR'""",
    )
    suspend fun countErrors(bookId: String): Int

    @Query("UPDATE segments SET state = :state, errorKind = :errorKind, errorDetail = :errorDetail WHERE id = :id")
    suspend fun setState(id: Long, state: SegmentState, errorKind: ErrorKind? = null, errorDetail: String? = null)

    @Query("UPDATE segments SET state = 'PROCESSING', attempts = attempts + 1 WHERE id = :id")
    suspend fun markProcessing(id: Long)

    /** Devuelve a la cola un segmento interrumpido (pausa, cancelación) sin contarlo como intento. */
    @Query("UPDATE segments SET state = 'PENDING', attempts = MAX(attempts - 1, 0) WHERE id = :id AND state = 'PROCESSING'")
    suspend fun release(id: Long)

    @Query("SELECT MAX(orderIndex) FROM segments WHERE chapterId = :chapterId")
    suspend fun lastIndex(chapterId: Long): Int?

    @Query("UPDATE segments SET state = 'PENDING', attempts = 0, errorKind = NULL, errorDetail = NULL WHERE bookId = :bookId AND state = 'ERROR'")
    suspend fun resetErrors(bookId: String)

    @Query("UPDATE segments SET state = 'PENDING', attempts = 0, errorKind = NULL, errorDetail = NULL WHERE bookId = :bookId")
    suspend fun resetAll(bookId: String)

    @Query(
        """SELECT s.id AS segmentId, c.id AS chapterId, c.orderIndex AS chapterOrder, c.title AS chapterTitle,
           s.orderIndex AS segmentOrder, t.path AS path, t.durationMs AS durationMs
           FROM segments s
           JOIN chapters c ON c.id = s.chapterId
           JOIN audio_tracks t ON t.segmentId = s.id
           WHERE s.bookId = :bookId AND c.included = 1 AND s.state = 'AVAILABLE'
           ORDER BY c.orderIndex, s.orderIndex""",
    )
    fun observePlayable(bookId: String): Flow<List<PlayableTrackRow>>

    @Query(
        """SELECT s.id AS segmentId, c.id AS chapterId, c.orderIndex AS chapterOrder, c.title AS chapterTitle,
           s.orderIndex AS segmentOrder, t.path AS path, t.durationMs AS durationMs
           FROM segments s
           JOIN chapters c ON c.id = s.chapterId
           JOIN audio_tracks t ON t.segmentId = s.id
           WHERE s.bookId = :bookId AND c.included = 1 AND s.state = 'AVAILABLE'
           ORDER BY c.orderIndex, s.orderIndex""",
    )
    suspend fun playable(bookId: String): List<PlayableTrackRow>

    @Query(
        """SELECT COUNT(*) AS segmentCount,
           COALESCE(SUM(CASE WHEN s.state = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS segmentsAvailable,
           COALESCE(SUM(s.charCount), 0) AS totalChars,
           COALESCE(SUM(CASE WHEN s.state = 'AVAILABLE' THEN s.charCount ELSE 0 END), 0) AS generatedChars,
           COALESCE(SUM(t.durationMs), 0) AS generatedDurationMs,
           COALESCE(SUM(t.sizeBytes), 0) AS audioSizeBytes
           FROM segments s
           JOIN chapters c ON c.id = s.chapterId
           LEFT JOIN audio_tracks t ON t.segmentId = s.id
           WHERE s.bookId = :bookId AND c.included = 1""",
    )
    suspend fun bookTotals(bookId: String): SegmentTotals
}

@Dao
interface AudioTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: AudioTrackEntity)

    @Query("SELECT path FROM audio_tracks WHERE bookId = :bookId")
    suspend fun pathsForBook(bookId: String): List<String>

    @Query("DELETE FROM audio_tracks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT * FROM audio_tracks WHERE bookId = :bookId")
    suspend fun forBook(bookId: String): List<AudioTrackEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM audio_tracks")
    fun observeTotalSize(): Flow<Long>
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM playback_progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): PlaybackProgressEntity?

    @Upsert
    suspend fun upsert(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM processing_jobs WHERE bookId = :bookId")
    suspend fun get(bookId: String): ProcessingJobEntity?

    @Query("SELECT * FROM processing_jobs WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ProcessingJobEntity?>

    @Query("SELECT * FROM processing_jobs WHERE state IN ('QUEUED', 'RUNNING')")
    fun observeActive(): Flow<List<ProcessingJobEntity>>

    /** Toda la cola, en el orden en que se procesará; los detenidos al final. */
    @Query(
        """SELECT * FROM processing_jobs WHERE state != 'DONE'
           ORDER BY CASE state WHEN 'RUNNING' THEN 0 WHEN 'QUEUED' THEN 1 ELSE 2 END, enqueuedAt""",
    )
    fun observeQueue(): Flow<List<ProcessingJobEntity>>

    @Query("SELECT COUNT(*) FROM processing_jobs WHERE state IN ('QUEUED', 'RUNNING')")
    suspend fun activeCount(): Int

    @Query("SELECT MIN(enqueuedAt) FROM processing_jobs WHERE state IN ('QUEUED', 'RUNNING')")
    suspend fun earliestEnqueuedAt(): Long?

    @Query("UPDATE processing_jobs SET enqueuedAt = :at WHERE bookId = :bookId")
    suspend fun setEnqueuedAt(bookId: String, at: Long)

    /** Otros libros que quedaron marcados en curso pasan a esperar su turno. */
    @Query("UPDATE processing_jobs SET state = 'QUEUED' WHERE state = 'RUNNING' AND bookId != :bookId")
    suspend fun requeueOthers(bookId: String)

    /**
     * Siguiente libro de la cola: el que lleva más tiempo esperando. Los que esperan conexión
     * para su voz no bloquean a los demás.
     */
    @Query(
        """SELECT * FROM processing_jobs WHERE state IN ('QUEUED', 'RUNNING')
           AND (errorKind IS NULL OR errorKind != 'NETWORK') ORDER BY enqueuedAt LIMIT 1""",
    )
    suspend fun next(): ProcessingJobEntity?

    /** Libros en cola que esperan conexión para su voz. */
    @Query("SELECT COUNT(*) FROM processing_jobs WHERE state = 'QUEUED' AND errorKind = 'NETWORK'")
    suspend fun waitingForNetwork(): Int

    /** Vuelve a intentar los que esperaban conexión: quizá ya la hay. */
    @Query("UPDATE processing_jobs SET errorKind = NULL, errorDetail = NULL WHERE state = 'QUEUED' AND errorKind = 'NETWORK'")
    suspend fun clearNetworkWaits()

    @Upsert
    suspend fun upsert(job: ProcessingJobEntity)

    @Query("UPDATE processing_jobs SET state = :state, errorKind = :errorKind, errorDetail = :errorDetail WHERE bookId = :bookId")
    suspend fun setState(bookId: String, state: JobState, errorKind: ErrorKind? = null, errorDetail: String? = null)

    @Query("UPDATE processing_jobs SET priorityChapterId = :chapterId WHERE bookId = :bookId")
    suspend fun setPriority(bookId: String, chapterId: Long?)

    @Query(
        """UPDATE processing_jobs SET currentChapterId = :chapterId, currentSegmentIndex = :segmentIndex,
           throughputCharsPerSecond = :throughput WHERE bookId = :bookId""",
    )
    suspend fun setCursor(bookId: String, chapterId: Long?, segmentIndex: Int, throughput: Float)

    @Query("DELETE FROM processing_jobs WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
