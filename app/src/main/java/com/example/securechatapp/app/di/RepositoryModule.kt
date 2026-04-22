package com.example.securechatapp.app.di

import com.example.securechatapp.core.crypto.DevCryptoEngine
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.repository.AuthRepositoryImpl
import com.example.securechatapp.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApi: AuthApi,
        sessionLocalDataSource: SessionLocalDataSource,
        crypto: DevCryptoEngine,
        json: Json,
    ): AuthRepository = AuthRepositoryImpl(
        authApi = authApi,
        sessionLocalDataSource = sessionLocalDataSource,
        crypto = crypto,
        json = json,
    )
}