package com.example.securechatapp.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.securechatapp.data.local.preferences.NotificationPreferenceDataSource
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.local.preferences.ThemePreferenceDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "secure_chat_theme")
private val Context.notificationDataStore by preferencesDataStore(name = "secure_chat_notifications")

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSessionLocalDataSource(
        @ApplicationContext context: Context,
    ): SecureSessionLocalDataSource = SecureSessionLocalDataSource(context)

    @Provides
    @Singleton
    fun provideThemePreferenceDataSource(
        @ApplicationContext context: Context,
    ): ThemePreferenceDataSource = ThemePreferenceDataSource(context.themeDataStore)

    @Provides
    @Singleton
    fun provideNotificationPreferenceDataSource(
        @ApplicationContext context: Context,
    ): NotificationPreferenceDataSource = NotificationPreferenceDataSource(context.notificationDataStore)
}
