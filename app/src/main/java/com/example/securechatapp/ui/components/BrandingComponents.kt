package com.example.securechatapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    animated: Boolean = false,
) {
    val pulse = if (animated) {
        rememberInfiniteTransition(label = "brand_mark").animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "brand_mark_scale",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier.scale(pulse),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape((size.value * 0.32f).dp),
            color = Color.Transparent,
            shadowElevation = 14.dp,
        ) {
            Box(
                modifier = Modifier.background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.96f),
                        )
                    )
                ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(size * 0.54f),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                ) {}

                Text(
                    text = "SC",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun BrandedEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 64.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BrandedSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    cornerRadius: Dp = 18.dp,
) {
    val shimmer = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    ).value

    Box(
        modifier = modifier
            .height(height)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f * shimmer),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * shimmer),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f * shimmer),
                    )
                ),
                shape = RoundedCornerShape(cornerRadius),
            )
    )
}

@Composable
fun BrandedSkeletonLines(
    modifier: Modifier = Modifier,
    primaryWidthFraction: Float = 1f,
    secondaryWidthFraction: Float = 0.62f,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandedSkeletonBlock(
            modifier = Modifier.fillMaxWidth(primaryWidthFraction),
            height = 18.dp,
            cornerRadius = 10.dp,
        )
        BrandedSkeletonBlock(
            modifier = Modifier.fillMaxWidth(secondaryWidthFraction),
            height = 12.dp,
            cornerRadius = 10.dp,
        )
    }
}
