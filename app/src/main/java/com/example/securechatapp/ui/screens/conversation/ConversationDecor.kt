package com.example.securechatapp.ui.screens.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.components.BrandedEmptyState
import com.example.securechatapp.ui.theme.SecureChatTheme

@Composable
fun TelegramChatWallpaper() {
    val dark = isSystemInDarkTheme()
    val extraColors = SecureChatTheme.extras
    val base = extraColors.wallpaper
    val accent1 = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.08f else 0.05f)
    val accent2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (dark) 0.05f else 0.035f)
    val accent3 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.12f else 0.07f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(base),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-56).dp, y = (-24).dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(accent1)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = 42.dp)
                .size(width = 210.dp, height = 84.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent2)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-34).dp, y = 12.dp)
                .size(width = 148.dp, height = 54.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent3)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 34.dp, y = 18.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(accent1)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = (-44).dp)
                .size(width = 180.dp, height = 68.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent2)
        )
    }
}

@Composable
fun DateSeparatorChip(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 1.dp,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun Banner(
    text: String,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val bg = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (onDismiss != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "✕",
                        color = content,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyConversationHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                ) {
                    BrandedEmptyState(
                        title = text,
                        subtitle = "Когда появятся сообщения, диалог оживёт здесь",
                    )
                }
            }
        }
    }
}
