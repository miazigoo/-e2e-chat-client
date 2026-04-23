package com.example.securechatapp.app

import android.app.Application
import com.example.securechatapp.app.runtime.AppRuntimeManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SecureChatApp : Application() {

    @Inject
    lateinit var appRuntimeManager: AppRuntimeManager

    override fun onCreate() {
        super.onCreate()
        appRuntimeManager.start()
    }
}
