package com.example.securechatapp.crypto.signal

import com.example.securechatapp.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SignalProtocolModule {

    @Binds
    @Singleton
    abstract fun bindSignalProtocolEngine(
        engine: DisabledSignalProtocolEngine,
    ): SignalProtocolEngine

    companion object {
        @Provides
        @Singleton
        fun provideSignalProtocolConfig(): SignalProtocolConfig {
            return SignalProtocolConfig(
                enabled = BuildConfig.ENABLE_SIGNAL_PROTOCOL,
            )
        }
    }
}
