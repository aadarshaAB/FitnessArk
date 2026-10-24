package com.fitnessark.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A grey placeholder block that gently pulses (static when system animations are off). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    // No transition at all under reduced motion, rather than one running with its value ignored
    val alpha = if (reduced) 0.7f else rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha"
    ).value
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
private fun SkeletonScreen(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) { content() }
}

@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) = SkeletonScreen(modifier) {
    SkeletonBlock(Modifier.fillMaxWidth().height(300.dp))
    SkeletonBlock(Modifier.fillMaxWidth().height(180.dp))
}

@Composable
fun ProgressSkeleton(modifier: Modifier = Modifier) = SkeletonScreen(modifier) {
    SkeletonBlock(Modifier.fillMaxWidth().height(40.dp))
    SkeletonBlock(Modifier.fillMaxWidth().height(260.dp))
}

@Composable
fun PhotosSkeleton(modifier: Modifier = Modifier) = SkeletonScreen(modifier) {
    SkeletonBlock(Modifier.fillMaxWidth().height(360.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(4) { SkeletonBlock(Modifier.weight(1f).height(72.dp)) }
    }
}
