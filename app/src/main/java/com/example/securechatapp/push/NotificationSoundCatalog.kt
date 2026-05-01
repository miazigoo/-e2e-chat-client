package com.example.securechatapp.push

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.RawRes
import com.example.securechatapp.R

data class NotificationSoundOption(
    val key: String,
    val label: String,
    @RawRes val resId: Int? = null,
)

object NotificationSoundCatalog {
    const val SYSTEM_DEFAULT_KEY = "system_default"
    const val SYSTEM_PICKED_KEY = "system_picked"

    private val bundledSounds = listOf(
        NotificationSoundOption("bundled_1", "Звук 1", R.raw.notification_sound_1),
        NotificationSoundOption("bundled_2", "Звук 2", R.raw.notification_sound_2),
        NotificationSoundOption("bundled_3", "Звук 3", R.raw.notification_sound_3),
        NotificationSoundOption("bundled_4", "Звук 4", R.raw.notification_sound_4),
        NotificationSoundOption("bundled_5", "Звук 5", R.raw.notification_sound_5),
        NotificationSoundOption("bundled_6", "Звук 6", R.raw.notification_sound_6),
        NotificationSoundOption("bundled_7", "Звук 7", R.raw.notification_sound_7),
        NotificationSoundOption("bundled_8", "Звук 8", R.raw.notification_sound_8),
        NotificationSoundOption("bundled_9", "Звук 9", R.raw.notification_sound_9),
        NotificationSoundOption("bundled_10", "Звук 10", R.raw.notification_sound_10),
        NotificationSoundOption("bundled_11", "Звук 11", R.raw.notification_sound_11),
        NotificationSoundOption("bundled_12", "Звук 12", R.raw.notification_sound_12),
    )

    val settingsOptions: List<NotificationSoundOption> = listOf(
        NotificationSoundOption(
            key = SYSTEM_DEFAULT_KEY,
            label = "Системный по умолчанию",
        )
    ) + bundledSounds

    fun resolveSoundUri(
        context: Context,
        soundKey: String,
        customSoundUri: String?,
    ): Uri? {
        return when (soundKey) {
            SYSTEM_DEFAULT_KEY -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            SYSTEM_PICKED_KEY -> customSoundUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> bundledSounds.firstOrNull { it.key == soundKey }?.resId?.let { resId ->
                Uri.parse("android.resource://${context.packageName}/$resId")
            } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    fun resolveSelectedLabel(
        context: Context,
        soundKey: String,
        customSoundUri: String?,
    ): String {
        return when (soundKey) {
            SYSTEM_DEFAULT_KEY -> "Системный по умолчанию"
            SYSTEM_PICKED_KEY -> customSoundUri
                ?.let(Uri::parse)
                ?.let { uri ->
                    runCatching {
                        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                    }.getOrNull()
                }
                ?.takeIf { it.isNotBlank() }
                ?.let { "Системный: $it" }
                ?: "Системный звук устройства"
            else -> bundledSounds.firstOrNull { it.key == soundKey }?.label ?: "Системный по умолчанию"
        }
    }
}
