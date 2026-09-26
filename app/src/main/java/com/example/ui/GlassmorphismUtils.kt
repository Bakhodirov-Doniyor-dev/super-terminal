package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OneUI 8.5 Ambient Backdrop Canvas.
 * Renders a deep, multi-dimensional backdrop with subtle ambient light spheres
 * that illuminate transparent glass panels and surfaces from behind.
 */
@Composable
fun OneUiAmbientBackdrop(
    modifier: Modifier = Modifier,
    theme: String = "ubuntu",
    content: @Composable () -> Unit
) {
    val (primaryGlow, secondaryGlow, baseGradient) = when (theme) {
        "ubuntu" -> Triple(
            Color(0xFFE95420).copy(alpha = 0.18f),
            Color(0xFF77216F).copy(alpha = 0.22f),
            listOf(Color(0xFF1E0A16), Color(0xFF0F040B), Color(0xFF080206))
        )
        "matrix" -> Triple(
            Color(0xFF00FF41).copy(alpha = 0.16f),
            Color(0xFF005511).copy(alpha = 0.22f),
            listOf(Color(0xFF001200), Color(0xFF000800), Color(0xFF000200))
        )
        "cyberpunk" -> Triple(
            Color(0xFFFF007F).copy(alpha = 0.20f),
            Color(0xFF7E00FF).copy(alpha = 0.20f),
            listOf(Color(0xFF1A052A), Color(0xFF0E0217), Color(0xFF06010B))
        )
        "monochrome" -> Triple(
            Color(0xFFFFFFFF).copy(alpha = 0.06f),
            Color(0xFF333333).copy(alpha = 0.15f),
            listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000))
        )
        "light" -> Triple(
            Color(0xFF38BDF8).copy(alpha = 0.15f),
            Color(0xFF818CF8).copy(alpha = 0.15f),
            listOf(Color(0xFFF8FAFC), Color(0xFFEDF2F7), Color(0xFFE2E8F0))
        )
        else -> Triple(
            Color(0xFFE95420).copy(alpha = 0.18f),
            Color(0xFF77216F).copy(alpha = 0.22f),
            listOf(Color(0xFF1E0A16), Color(0xFF0F040B), Color(0xFF080206))
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Base deep radial/linear background
                drawRect(
                    brush = Brush.verticalGradient(baseGradient)
                )
                // Top-right ambient glow orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.75f
                    )
                )
                // Bottom-left secondary ambient glow orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.width * 0.85f
                    )
                )
            }
    ) {
        content()
    }
}

/**
 * OneUI 8.5 Smooth Fading Edge.
 * Fades out the top and bottom of any scrolling view progressively with zero cutoffs.
 * Uses a unified hardware-accelerated shader pass with CompositingStrategy.Offscreen.
 */
fun Modifier.verticalFadingEdge(
    topEdgeSize: Dp = 42.dp,
    bottomEdgeSize: Dp = 42.dp
) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent

        val topPx = topEdgeSize.toPx().coerceAtLeast(0f)
        val bottomPx = bottomEdgeSize.toPx().coerceAtLeast(0f)

        if (topPx == 0f && bottomPx == 0f) return@drawWithContent

        val topStop = (topPx / size.height).coerceIn(0f, 0.49f)
        val bottomStop = (1f - (bottomPx / size.height)).coerceIn(0.51f, 1f)

        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Transparent,
                topStop to Color.Black,
                bottomStop to Color.Black,
                1.0f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * Dynamic Scroll-Aware OneUI 8.5 Fading Edge for ScrollState.
 * Fades the top edge only when scrolled down, and fades the bottom edge only when scrollable content remains.
 */
fun Modifier.verticalFadingEdge(
    scrollState: ScrollState,
    edgeSize: Dp = 42.dp
) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent

        val edgePx = edgeSize.toPx().coerceAtLeast(0f)
        val isScrolledFromTop = scrollState.value > 4
        val canScrollDown = scrollState.value < scrollState.maxValue - 4

        val topPx = if (isScrolledFromTop) edgePx else 0f
        val bottomPx = if (canScrollDown) edgePx else 0f

        if (topPx == 0f && bottomPx == 0f) return@drawWithContent

        val topStop = (topPx / size.height).coerceIn(0.001f, 0.49f)
        val bottomStop = (1f - (bottomPx / size.height)).coerceIn(0.51f, 0.999f)

        val topColor = if (isScrolledFromTop) Color.Transparent else Color.Black
        val bottomColor = if (canScrollDown) Color.Transparent else Color.Black

        drawRect(
            brush = Brush.verticalGradient(
                0.0f to topColor,
                topStop to Color.Black,
                bottomStop to Color.Black,
                1.0f to bottomColor
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * Dynamic Scroll-Aware OneUI 8.5 Fading Edge for LazyListState.
 */
fun Modifier.verticalFadingEdge(
    lazyListState: LazyListState,
    edgeSize: Dp = 42.dp
) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent

        val edgePx = edgeSize.toPx().coerceAtLeast(0f)
        val isScrolledFromTop = lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 4
        val canScrollDown = lazyListState.canScrollForward

        val topPx = if (isScrolledFromTop) edgePx else 0f
        val bottomPx = if (canScrollDown) edgePx else 0f

        if (topPx == 0f && bottomPx == 0f) return@drawWithContent

        val topStop = (topPx / size.height).coerceIn(0.001f, 0.49f)
        val bottomStop = (1f - (bottomPx / size.height)).coerceIn(0.51f, 0.999f)

        val topColor = if (isScrolledFromTop) Color.Transparent else Color.Black
        val bottomColor = if (canScrollDown) Color.Transparent else Color.Black

        drawRect(
            brush = Brush.verticalGradient(
                0.0f to topColor,
                topStop to Color.Black,
                bottomStop to Color.Black,
                1.0f to bottomColor
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * OneUI 8.5 Frosted Glass Window Panel / Card modifier.
 * Features translucent frosted fill with specular highlight border gradient.
 */
fun Modifier.oneUiGlassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = Color(0xFF161A22).copy(alpha = 0.65f),
    borderColor: Color = Color.White.copy(alpha = 0.22f),
    borderWidth: Dp = 1.dp
) = this
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = (backgroundColor.alpha * 1.15f).coerceAtMost(0.96f)),
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.85f).coerceAtLeast(0.15f))
            )
        )
    )
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                borderColor.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.12f)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        ),
        shape = shape
    )

/**
 * OneUI 8.5 Glass Capsule Button modifier.
 * Transforms interactive buttons into translucent glass capsules with specular rim lighting.
 */
fun Modifier.oneUiGlassCapsule(
    backgroundColor: Color = Color.White.copy(alpha = 0.14f),
    accentColor: Color = Color.White,
    borderColor: Color = accentColor,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    shape: Shape = CircleShape
) = this
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = (backgroundColor.alpha * 1.35f).coerceAtMost(0.95f)),
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.75f).coerceAtLeast(0.12f))
            )
        ),
        shape = shape
    )
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                (if (borderColor != accentColor) borderColor else accentColor).copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.15f)
            )
        ),
        shape = shape
    )

// Backward compatibility alias functions
fun Modifier.glassBackground(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color = Color.White.copy(alpha = 0.20f),
    borderWidth: Dp = 1.dp
) = this.oneUiGlassCard(shape, backgroundColor, borderColor, borderWidth)

fun Modifier.glassCapsule(
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    borderColor: Color = Color.White.copy(alpha = 0.25f)
) = this.oneUiGlassCapsule(backgroundColor = backgroundColor, borderColor = borderColor)

/**
 * Master OneUI 8.5 Interactive Glass Capsule Button Composable.
 * Provides micro-spring press scaling, specular glass gradient, and accessibility support.
 */
@Composable
fun OneUiGlassCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.White.copy(alpha = 0.14f),
    accentColor: Color = Color.White,
    contentColor: Color = Color.White,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    shape: Shape = CircleShape,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "glass_capsule_scale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
        interactionSource = interactionSource,
        modifier = modifier
            .scale(scale)
            .oneUiGlassCapsule(
                backgroundColor = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.06f),
                accentColor = if (enabled) accentColor else Color.Gray.copy(alpha = 0.18f),
                shape = shape
            )
    ) {
        Row(
            modifier = Modifier.padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Convenient OneUI 8.5 Glass Capsule Button with text and optional icon / loading indicator.
 */
@Composable
fun OneUiGlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = Color(0xFF10B981),
    textColor: Color = Color.White,
    isPrimary: Boolean = false,
    isLoading: Boolean = false,
    shape: Shape = CircleShape
) {
    val bgColor = if (isPrimary) {
        accentColor.copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
    val rimColor = if (isPrimary) accentColor else Color.White.copy(alpha = 0.4f)
    val fontColor = if (isPrimary) accentColor else textColor

    OneUiGlassCapsuleButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        backgroundColor = bgColor,
        accentColor = rimColor,
        contentColor = fontColor,
        shape = shape,
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = fontColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fontColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        androidx.compose.material3.Text(
            text = text,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = fontColor
        )
    }
}
