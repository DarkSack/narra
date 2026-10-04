package app.narra.data.repository

import androidx.room.withTransaction
import app.narra.data.db.NarraDatabase
import app.narra.data.db.PlaybackProgressEntity
import app.narra.data.db.toDomain
import app.narra.domain.model.ListeningProgress
import app.narra.domain.model.PlayableTrack
import app.narra.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl @Inject constructor(private val db: NarraDatabase) : PlaybackRepository {

    override fun observePlayableTracks(bookId: String): Flow<List<PlayableTrack>> =
        db.segmentDao().observePlayable(bookId)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun playableTracks(bookId: String): List<PlayableTrack> =
        db.segmentDao().playable(bookId).map { it.toDomain() }

    override fun observeLastPlayedBookId(): Flow<String?> = db.bookDao().observeLastPlayedId().distinctUntilChanged()

    override suspend fun getProgress(bookId: String): ListeningProgress? = db.progressDao().get(bookId)?.toDomain()

    override suspend fun saveProgress(progress: ListeningProgress) {
        db.withTransaction {
            // El libro pudo borrarse mientras sonaba: no resucitar su progreso.
            if (db.bookDao().get(progress.bookId) == null) return@withTransaction
            db.progressDao().upsert(
                PlaybackProgressEntity(
                    bookId = progress.bookId,
                    segmentId = progress.segmentId,
                    chapterId = progress.chapterId,
                    chapterTitle = progress.chapterTitle,
                    positionMs = progress.positionMs,
                    listenedMs = progress.listenedMs,
                    completed = progress.completed,
                    updatedAt = progress.updatedAt,
                ),
            )
            db.bookDao().touchPlayed(progress.bookId, progress.updatedAt)
        }
    }
}
