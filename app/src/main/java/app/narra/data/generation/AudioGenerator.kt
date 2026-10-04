package app.narra.data.generation

import android.os.SystemClock
import androidx.room.withTransaction
import app.narra.audio.SegmentAudioRenderer
import app.narra.data.db.AudioTrackEntity
import app.narra.data.db.BookEntity
import app.narra.data.db.NarraDatabase
import app.narra.data.db.ProcessingJobEntity
import app.narra.data.db.SegmentEntity
import app.narra.data.db.toParagraphs
import app.narra.data.db.voiceSettings
import app.narra.data.files.BookStorage
import app.narra.data.repository.BookStatsUpdater
import app.narra.domain.model.BookState
import app.narra.domain.model.ChapterState
import app.narra.domain.model.ErrorKind
import app.narra.domain.model.JobState
import app.narra.domain.model.NarraException
import app.narra.domain.model.SegmentState
import app.narra.domain.tts.TtsProviders
import app.narra.domain.tts.TtsSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que el procesador cuenta mientras trabaja, para la notificación. */
sealed interface GenerationEvent {
    val bookId: String
    val title: String

    data class Progress(
        override val bookId: String,
        override val title: String,
        val chapterNumber: Int,
        val chapterCount: Int,
        val chaptersAvailable: Int,
        val segmentsAvailable: Int,
        val segmentCount: Int,
    ) : GenerationEvent

    data class Finished(override val bookId: String, override val title: String) : GenerationEvent

    data class Failed(override val bookId: String, override val title: String, val kind: ErrorKind) : GenerationEvent
}

/**
 * Procesa la cola de generación: un segmento cada vez, del libro que toca. Antes de cada
 * segmento vuelve a mirar la cola, así un libro adelantado o un capítulo pedido por el usuario
 * pasan delante sin esperar a que termine el libro en curso.
 */
@Singleton
class AudioGenerator @Inject constructor(
    private val db: NarraDatabase,
    private val storage: BookStorage,
    private val stats: BookStatsUpdater,
    private val providers: TtsProviders,
    private val renderer: SegmentAudioRenderer,
) {
    private val books get() = db.bookDao()
    private val chapters get() = db.chapterDao()
    private val segments get() = db.segmentDao()
    private val jobs get() = db.jobDao()

    suspend fun runQueue(onEvent: suspend (GenerationEvent) -> Unit) {
        val sessions = HashMap<String, TtsSession>()
        try {
            while (currentCoroutineContext().isActive) {
                val job = jobs.next() ?: break
                val book = books.get(job.bookId)
                if (book == null) {
                    jobs.delete(job.bookId)
                    continue
                }
                if (job.state != JobState.RUNNING) start(book)

                val segment = nextSegment(job)
                if (segment == null) {
                    finish(book)?.let { onEvent(it) }
                    continue
                }
                onEvent(progress(book.id, segment))
                val session = try {
                    sessions.getOrPut(book.voiceProviderId) { openSession(book.voiceProviderId) }
                } catch (e: NarraException) {
                    stopBook(book, e)
                    onEvent(GenerationEvent.Failed(book.id, book.title, e.kind))
                    continue
                }
                when (val outcome = generate(book, job, segment, session)) {
                    is Outcome.Fatal -> {
                        sessions.remove(book.voiceProviderId)?.close()
                        stopBook(book, outcome.error)
                        onEvent(GenerationEvent.Failed(book.id, book.title, outcome.error.kind))
                    }
                    Outcome.Done, Outcome.Interrupted, Outcome.WillRetry -> Unit
                }
            }
        } finally {
            sessions.values.forEach { it.close() }
        }
    }

    private suspend fun start(book: BookEntity) = db.withTransaction {
        jobs.requeueOthers(book.id)
        jobs.setState(book.id, JobState.RUNNING)
        books.setState(book.id, BookState.GENERATING)
    }

    private suspend fun nextSegment(job: ProcessingJobEntity): SegmentEntity? {
        val fromOrder = job.priorityChapterId?.let { chapters.get(it)?.orderIndex } ?: 0
        return segments.nextToGenerate(job.bookId, fromOrder)
    }

    private suspend fun openSession(providerId: String): TtsSession {
        val provider = providers.get(providerId) ?: throw NarraException(ErrorKind.TTS_UNAVAILABLE, "proveedor $providerId")
        return provider.open()
    }

    private sealed interface Outcome {
        data object Done : Outcome
        data object Interrupted : Outcome
        data object WillRetry : Outcome
        data class Fatal(val error: NarraException) : Outcome
    }

    private suspend fun generate(book: BookEntity, job: ProcessingJobEntity, segment: SegmentEntity, session: TtsSession): Outcome {
        segments.markProcessing(segment.id)
        val chapter = chapters.get(segment.chapterId)
        if (chapter != null && chapter.state == ChapterState.PENDING) chapters.setState(chapter.id, ChapterState.PROCESSING)
        val voice = book.voiceSettings()
        val target = storage.audioFile(book.id, segment.chapterId, segment.id, voice.format)
        val endsChapter = segments.lastIndex(segment.chapterId) == segment.orderIndex
        val started = SystemClock.elapsedRealtime()
        return try {
            val rendered = whileJobActive(book.id) {
                renderer.render(session, segment.paragraphsJson.toParagraphs(), voice, endsChapter, target)
            }
            if (rendered == null) {
                release(segment)
                return Outcome.Interrupted
            }
            val saved = db.withTransaction {
                // El usuario pudo pausar o borrar el audio justo al terminar el segmento.
                if (jobs.get(book.id)?.state !in ACTIVE_STATES) return@withTransaction false
                db.audioTrackDao().upsert(
                    AudioTrackEntity(
                        segmentId = segment.id,
                        bookId = book.id,
                        chapterId = segment.chapterId,
                        path = target.absolutePath,
                        durationMs = rendered.durationMs,
                        sizeBytes = rendered.sizeBytes,
                        format = voice.format,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                segments.setState(segment.id, SegmentState.AVAILABLE)
                true
            }
            if (!saved) {
                withContext(NonCancellable + Dispatchers.IO) { target.delete() }
                release(segment)
                return Outcome.Interrupted
            }
            val seconds = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1) / MS_PER_SECOND
            jobs.setCursor(book.id, segment.chapterId, segment.orderIndex, smoothed(job.throughputCharsPerSecond, segment.charCount / seconds))
            stats.refreshBook(book.id)
            Outcome.Done
        } catch (e: CancellationException) {
            release(segment)
            throw e
        } catch (e: Exception) {
            val error = e.toNarraException()
            if (error.kind in FATAL) {
                release(segment)
                return Outcome.Fatal(error)
            }
            val attempts = segment.attempts + 1
            if (attempts >= MAX_ATTEMPTS) {
                segments.setState(segment.id, SegmentState.ERROR, error.kind, error.message)
                chapters.setState(segment.chapterId, ChapterState.ERROR, error.kind, error.message)
                stats.refreshBook(book.id)
            } else {
                segments.setState(segment.id, SegmentState.PENDING)
                delay(RETRY_BACKOFF_MS * attempts)
            }
            Outcome.WillRetry
        }
    }

    /**
     * Ejecuta [block] mientras el trabajo del libro siga activo. Si el usuario lo pausa o lo
     * cancela, interrumpe el segmento en curso y devuelve null.
     */
    private suspend fun <T> whileJobActive(bookId: String, block: suspend () -> T): T? = coroutineScope {
        val work = async { block() }
        val watcher = launch {
            jobs.observe(bookId).first { it == null || it.state !in ACTIVE_STATES }
            work.cancel()
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            null
        } finally {
            watcher.cancel()
        }
    }

    /** Devuelve el segmento a la cola y deja su capítulo como estaba si no llegó a tener audio. */
    private suspend fun release(segment: SegmentEntity) = withContext(NonCancellable) {
        segments.release(segment.id)
        val chapter = chapters.get(segment.chapterId) ?: return@withContext
        if (chapter.state == ChapterState.PROCESSING) {
            chapters.setState(chapter.id, ChapterState.PENDING)
            stats.refreshChapter(chapter.id)
        }
    }

    /** Cierra el trabajo cuando no quedan segmentos pendientes. */
    private suspend fun finish(book: BookEntity): GenerationEvent? {
        stats.refreshBook(book.id)
        val failed = segments.countErrors(book.id)
        return db.withTransaction {
            if (failed > 0) {
                jobs.setState(book.id, JobState.FAILED, ErrorKind.SYNTHESIS_FAILED, "$failed")
                books.setState(book.id, BookState.ERROR, ErrorKind.SYNTHESIS_FAILED, "$failed")
                GenerationEvent.Failed(book.id, book.title, ErrorKind.SYNTHESIS_FAILED)
            } else {
                jobs.delete(book.id)
                books.setState(book.id, BookState.COMPLETED)
                GenerationEvent.Finished(book.id, book.title)
            }
        }
    }

    private suspend fun stopBook(book: BookEntity, error: NarraException) = db.withTransaction {
        jobs.setState(book.id, JobState.FAILED, error.kind, error.message)
        books.setState(book.id, BookState.ERROR, error.kind, error.message)
    }

    private suspend fun progress(bookId: String, segment: SegmentEntity): GenerationEvent.Progress {
        val book = books.get(bookId)
        val included = chapters.getAll(bookId).filter { it.included }
        return GenerationEvent.Progress(
            bookId = bookId,
            title = book?.title.orEmpty(),
            chapterNumber = included.indexOfFirst { it.id == segment.chapterId } + 1,
            chapterCount = included.size,
            chaptersAvailable = book?.chaptersAvailable ?: 0,
            segmentsAvailable = book?.segmentsAvailable ?: 0,
            segmentCount = book?.segmentCount ?: 0,
        )
    }

    private fun smoothed(previous: Float, measured: Float): Float =
        if (previous <= 0f) measured else previous * (1 - THROUGHPUT_SMOOTHING) + measured * THROUGHPUT_SMOOTHING

    private fun Exception.toNarraException(): NarraException = when (this) {
        is NarraException -> this
        is IOException -> if (storage.availableBytes() < LOW_STORAGE_BYTES) {
            NarraException(ErrorKind.STORAGE_FULL, message, this)
        } else {
            NarraException(ErrorKind.ENCODING_FAILED, message, this)
        }
        else -> NarraException(ErrorKind.ENCODING_FAILED, message, this)
    }

    private companion object {
        val ACTIVE_STATES = setOf(JobState.QUEUED, JobState.RUNNING)

        /** Errores que no se arreglan reintentando el mismo segmento: se detiene el libro. */
        val FATAL = setOf(ErrorKind.TTS_UNAVAILABLE, ErrorKind.VOICE_UNAVAILABLE, ErrorKind.STORAGE_FULL, ErrorKind.NETWORK)

        const val MAX_ATTEMPTS = 3
        const val RETRY_BACKOFF_MS = 2_000L
        const val MS_PER_SECOND = 1_000f
        const val THROUGHPUT_SMOOTHING = 0.3f
        const val LOW_STORAGE_BYTES = 50L * 1024 * 1024
    }
}
