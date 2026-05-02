package com.example.securechatapp.ui.navigation

object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"
    const val VerifyEmailCode = "verify_email_code"
    const val Chats = "chats"
    const val Settings = "settings"

    const val ConversationArg = "conversationId"
    const val ConversationPattern = "conversation/{$ConversationArg}"
    const val ConversationMediaPattern = "conversation/{$ConversationArg}/media"

    fun conversationRoute(conversationId: Int): String = "conversation/$conversationId"
    fun conversationMediaRoute(conversationId: Int): String = "conversation/$conversationId/media"
}
