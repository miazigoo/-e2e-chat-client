package com.example.securechatapp.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.example.securechatapp.data.remote.websocket.RealtimeConnectionState
import com.example.securechatapp.ui.theme.TgTopBarDark
import com.example.securechatapp.ui.theme.TgTopBarLight

@Composable
fun ConversationTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean,
    realtimeState: RealtimeConnectionState,
) {
    val dark = isSystemInDarkTheme()
    val statusDotColor = when (realtimeState) {
        RealtimeConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        RealtimeConnectionState.CONNECTING,
        RealtimeConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
        RealtimeConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = if (dark) TgTopBarDark else TgTopBarLight,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("←")
            }

            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title.trim().removePrefix("@").firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = statusDotColor,
                    ) {}

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                modifier = Modifier.clickable(onClick = onRefresh),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            ) {
                Text(
                    text = "Обновить",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            TextButton(onClick = onLogout) {
                Text(if (isLoggingOut) "..." else "Выйти")
            }
        }
    }
}
