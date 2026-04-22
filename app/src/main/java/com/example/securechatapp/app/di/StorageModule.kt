package com.example.securechatapp.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.local.preferences.ThemePreferenceDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "secure_chat_session")

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSessionLocalDataSource(
        @ApplicationContext context: Context,
    ): SessionLocalDataSource = SessionLocalDataSource(context.sessionDataStore)

    @Provides
    @Singleton
    fun provideThemePreferenceDataSource(
        @ApplicationContext context: Context,
    ): ThemePreferenceDataSource = ThemePreferenceDataSource(context.sessionDataStore)
}
