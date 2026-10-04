package app.narra.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        SegmentEntity::class,
        AudioTrackEntity::class,
        PlaybackProgressEntity::class,
        ProcessingJobEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: motor de voz de cada libro (books.voiceEngine).
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class NarraDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun segmentDao(): SegmentDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun progressDao(): ProgressDao
    abstract fun jobDao(): JobDao

    companion object {
        const val NAME = "narra.db"
    }
}
