package com.example.securechatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun TelegramAuthScaffold(
    title: String,
    subtitle: String,
    bottomOverlay: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(size = 84.dp)

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 20.dp),
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }

        bottomOverlay?.let { overlay ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp,
                    )
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                overlay()
            }
        }
    }
}

@Composable
fun TelegramStatusCard(
    text: String,
    isError: Boolean = false,
    bottomSheetStyle: Boolean = false,
) {
    val background = if (isError) {
        if (bottomSheetStyle) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        }
    } else {
        if (bottomSheetStyle) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        }
    }

    val contentColor = if (isError) {
        if (bottomSheetStyle) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.error
        }
    } else {
        if (bottomSheetStyle) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        }
    }

    Surface(
        modifier = if (bottomSheetStyle) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (bottomSheetStyle) 22.dp else 18.dp),
        color = background,
        tonalElevation = if (bottomSheetStyle) 6.dp else 0.dp,
        shadowElevation = if (bottomSheetStyle) 8.dp else 0.dp,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                horizontal = if (bottomSheetStyle) 16.dp else 14.dp,
                vertical = if (bottomSheetStyle) 14.dp else 12.dp,
            ),
        )
    }
}
