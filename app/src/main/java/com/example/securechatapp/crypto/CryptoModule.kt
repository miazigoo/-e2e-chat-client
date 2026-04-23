package com.example.securechatapp.crypto

import com.example.securechatapp.crypto.engine.CryptoEngine
import com.yourapp.data.crypto.RealCryptoEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideCryptoEngine(): CryptoEngine {
        return RealCryptoEngine()
    }
}