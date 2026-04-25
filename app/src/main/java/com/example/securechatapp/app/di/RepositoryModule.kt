package com.example.securechatapp.app.di

import com.example.securechatapp.crypto.signal.LibSignalMessageCryptoEngine
import com.example.securechatapp.crypto.signal.RealSignalBootstrapKeyMaterialProvider
import com.example.securechatapp.crypto.signal.RealSignalPreKeyMaterialProvider
import com.example.securechatapp.crypto.signal.SignalBootstrapKeyMaterialProvider
import com.example.securechatapp.crypto.signal.SignalMessageCryptoEngine
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
        signalBootstrapKeyMaterialProvider: SignalBootstrapKeyMaterialProvider,
        json: Json,
    ): AuthRepository = AuthRepositoryImpl(
        authApi = authApi,
        sessionLocalDataSource = sessionLocalDataSource,
        signalBootstrapKeyMaterialProvider = signalBootstrapKeyMaterialProvider,
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
        provider: RealSignalPreKeyMaterialProvider,
    ): SignalPreKeyMaterialProvider = provider

    @Provides
    @Singleton
    fun provideSignalBootstrapKeyMaterialProvider(
        provider: RealSignalBootstrapKeyMaterialProvider,
    ): SignalBootstrapKeyMaterialProvider = provider

    @Provides
    @Singleton
    fun provideSignalMessageCryptoEngine(
        engine: LibSignalMessageCryptoEngine,
    ): SignalMessageCryptoEngine = engine
}
