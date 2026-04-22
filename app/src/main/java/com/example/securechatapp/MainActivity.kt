package com.example.securechatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.securechatapp.ui.navigation.SecureChatNavHost
import com.example.securechatapp.ui.theme.SecureChatAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecureChatAppTheme {
                SecureChatNavHost()
            }
        }
    }
}