package kr.co.nuni.gildam.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kr.co.nuni.gildam.data.local.GildamDatabase
import kr.co.nuni.gildam.data.local.dao.GildamDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideGildamDatabase(
        @ApplicationContext context: Context
    ): GildamDatabase {
        return Room.databaseBuilder(
            context,
            GildamDatabase::class.java,
            "gildam_offline_first.db"
        )
            // 1초 주기 위치 기록 시 I/O 경합을 줄이기 위해 WAL(Write-Ahead Logging) 활성화
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideGildamDao(db: GildamDatabase): GildamDao = db.gildamDao()

    @Provides
    fun provideCourseDao(db: GildamDatabase): kr.co.nuni.gildam.data.course.CourseDao = db.courseDao()
}
