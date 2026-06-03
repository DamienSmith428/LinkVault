package com.linkvault.di

import android.content.Context
import androidx.room.Room
import com.linkvault.data.local.dao.DownloadDao
import com.linkvault.data.local.dao.FolderDao
import com.linkvault.data.local.dao.LinkDao
import com.linkvault.data.local.database.LinkVaultDatabase
import com.linkvault.data.local.database.LinkVaultDatabase.Companion.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LinkVaultDatabase {
        return Room.databaseBuilder(
            context,
            LinkVaultDatabase::class.java,
            "linkvault.db"
        )
        .addMigrations(MIGRATION_2_3)
        .build()
    }

    @Provides
    fun provideFolderDao(db: LinkVaultDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideLinkDao(db: LinkVaultDatabase): LinkDao = db.linkDao()

    @Provides
    fun provideDownloadDao(db: LinkVaultDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }
}
