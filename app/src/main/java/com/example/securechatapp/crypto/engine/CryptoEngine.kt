package com.example.securechatapp.crypto.engine

interface CryptoEngine {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}
