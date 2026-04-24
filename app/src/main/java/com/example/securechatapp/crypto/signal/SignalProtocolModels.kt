package com.example.securechatapp.crypto.signal

data class SignalAddress(
    val userId: Int,
    val deviceId: Int,
)

data class SignalPlaintextMessage(
    val conversationId: Int,
    val sender: SignalAddress,
    val recipient: SignalAddress,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalPlaintextMessage) return false

        return conversationId == other.conversationId &&
                sender == other.sender &&
                recipient == other.recipient &&
                body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = conversationId
        result = 31 * result + sender.hashCode()
        result = 31 * result + recipient.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

data class SignalCiphertextMessage(
    val conversationId: Int,
    val sender: SignalAddress,
    val recipient: SignalAddress,
    val type: SignalCiphertextType,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalCiphertextMessage) return false

        return conversationId == other.conversationId &&
                sender == other.sender &&
                recipient == other.recipient &&
                type == other.type &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = conversationId
        result = 31 * result + sender.hashCode()
        result = 31 * result + recipient.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

enum class SignalCiphertextType {
    PRE_KEY,
    WHISPER,
    SENDER_KEY,
}

data class SignalPreKeyBundleDraft(
    val registrationId: Int,
    val deviceId: Int,
    val identityKey: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyId: Int?,
    val oneTimePreKeyPublic: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalPreKeyBundleDraft) return false

        return registrationId == other.registrationId &&
                deviceId == other.deviceId &&
                identityKey.contentEquals(other.identityKey) &&
                signedPreKeyId == other.signedPreKeyId &&
                signedPreKeyPublic.contentEquals(other.signedPreKeyPublic) &&
                signedPreKeySignature.contentEquals(other.signedPreKeySignature) &&
                oneTimePreKeyId == other.oneTimePreKeyId &&
                nullableContentEquals(oneTimePreKeyPublic, other.oneTimePreKeyPublic)
    }

    override fun hashCode(): Int {
        var result = registrationId
        result = 31 * result + deviceId
        result = 31 * result + identityKey.contentHashCode()
        result = 31 * result + signedPreKeyId
        result = 31 * result + signedPreKeyPublic.contentHashCode()
        result = 31 * result + signedPreKeySignature.contentHashCode()
        result = 31 * result + (oneTimePreKeyId ?: 0)
        result = 31 * result + (oneTimePreKeyPublic?.contentHashCode() ?: 0)
        return result
    }
}

private fun nullableContentEquals(left: ByteArray?, right: ByteArray?): Boolean {
    return when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> left.contentEquals(right)
    }
}
