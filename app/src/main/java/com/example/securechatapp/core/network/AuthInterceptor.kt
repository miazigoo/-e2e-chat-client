package com.example.securechatapp.core.network

import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionLocalDataSource: SessionLocalDataSource,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val session = runBlocking { sessionLocalDataSource.getSessionSnapshot() }

        val builder = original.newBuilder()

        session?.accessToken?.takeIf { it.isNotBlank() }?.let {
            if (original.header("Authorization").isNullOrBlank()) {
                builder.header("Authorization", "Bearer $it")
            }
        }

        session?.deviceUuid?.takeIf { it.isNotBlank() }?.let {
            if (original.header("X-Device-UUID").isNullOrBlank()) {
                builder.header("X-Device-UUID", it)
            }
        }

        return chain.proceed(builder.build())
    }
}