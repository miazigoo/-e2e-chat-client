package com.example.securechatapp.ui.screens.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.theme.SecureChatTheme

@Composable
fun ConversationTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSharedSecretClick: () -> Unit,
    isLoggingOut: Boolean,
    sharedSecretEnabled: Boolean,
    localSharedSecretEnabled: Boolean,
) {
    val extraColors = SecureChatTheme.extras
    val lockLabel = when {
        sharedSecretEnabled && localSharedSecretEnabled -> "E2E активно"
        sharedSecretEnabled -> "Нужен токен"
        else -> "Базовая защита"
    }
    val lockEmoji = when {
        sharedSecretEnabled && localSharedSecretEnabled -> "🔐"
        sharedSecretEnabled -> "🔒"
        else -> "🔓"
    }
    val lockColor = when {
        sharedSecretEnabled && localSharedSecretEnabled -> MaterialTheme.colorScheme.primaryContainer
        sharedSecretEnabled -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = extraColors.topBar,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleActionButton(
                label = "←",
                onClick = onBack,
            )

            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title.trim().removePrefix("@").firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = lockColor,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    ),
                    modifier = Modifier.padding(top = 5.dp),
                ) {
                    Text(
                        text = "$lockEmoji $lockLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            CircleActionButton(
                label = lockEmoji,
                onClick = onSharedSecretClick,
            )

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !isLoggingOut, onClick = onLogout),
            ) {
                Text(
                    text = if (isLoggingOut) "..." else "Выйти",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
