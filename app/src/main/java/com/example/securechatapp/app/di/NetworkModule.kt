package com.example.securechatapp.app.di

import com.example.securechatapp.BuildConfig
import com.example.securechatapp.core.network.AuthAuthenticator
import com.example.securechatapp.core.network.AuthInterceptor
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.ENABLE_HTTP_LOGGING) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        sessionLocalDataSource: SessionLocalDataSource,
    ): AuthInterceptor = AuthInterceptor(sessionLocalDataSource)

    @Provides
    @Singleton
    fun provideAuthAuthenticator(
        sessionLocalDataSource: SessionLocalDataSource,
        json: Json,
    ): AuthAuthenticator = AuthAuthenticator(
        baseUrl = BuildConfig.API_BASE_URL,
        sessionLocalDataSource = sessionLocalDataSource,
        json = json,
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(authAuthenticator)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(
        retrofit: Retrofit,
    ): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideChatBackendApi(
        retrofit: Retrofit,
    ): ChatBackendApi = retrofit.create(ChatBackendApi::class.java)
}