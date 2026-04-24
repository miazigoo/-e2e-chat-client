package com.example.securechatapp.app.di

import com.example.securechatapp.crypto.engine.CryptoEngine
import com.example.securechatapp.crypto.signal.FailClosedSignalPreKeyMaterialProvider
import com.example.securechatapp.crypto.signal.SignalPreKeyMaterialProvider
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.remote.api.KeysApi
import com.example.securechatapp.data.repository.AuthRepositoryImpl
import com.example.securechatapp.domain.repository.AuthRepository
import com.example.securechatapp.data.repository.SignalPreKeyRepositoryImpl
import com.example.securechatapp.domain.repository.SignalPreKeyRepository
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
        sessionLocalDataSource: SecureSessionLocalDataSource,
        crypto: CryptoEngine,
        json: Json,
    ): AuthRepository = AuthRepositoryImpl(
        authApi = authApi,
        sessionLocalDataSource = sessionLocalDataSource,
        crypto = crypto,
        json = json,
    )


    @Provides
    @Singleton
    fun provideSignalPreKeyRepository(
        keysApi: KeysApi,
        json: Json,
    ): SignalPreKeyRepository = SignalPreKeyRepositoryImpl(
        keysApi = keysApi,
        json = json,
    )

    @Provides
    @Singleton
    fun provideSignalPreKeyMaterialProvider(
        provider: FailClosedSignalPreKeyMaterialProvider,
    ): SignalPreKeyMaterialProvider = provider
}
