package com.owlcoders.chitti.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.ui.theme.*
import kotlin.math.roundToInt

/*
 * The component kit. Every screen is built from these so spacing, radii, borders, type and colour
 * stay identical across the app. Screens should not hand-roll a Card, a button, a chip or an empty
 * state: if something is missing here, add it here.
 */

/** 4pt spacing scale. Use these instead of arbitrary dp values. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Gutter for every screen edge. */
    val gutter = 20.dp
}

/** Full-bleed page background plus the standard side gutter. */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink),
        content = content
    )
}

/**
 * Large screen title with optional subtitle and a trailing action slot.
 * One per screen, always at the top, always the same metrics.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    revealTitle: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.gutter, top = Space.xl, bottom = Space.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Large title: every screen answers "where am I" at a glance.
            if (revealTitle) {
                BlurText(text = title, style = MaterialTheme.typography.headlineLarge, color = TextHigh)
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextHigh
                )
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
            }
        }
        trailing()
    }
}

/** Uppercase overline that opens a group of rows or cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = TextLow,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.l, bottom = Space.s)
    )
}

/** Hairline separator used inside cards and between rows. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(Hairline)
    )
}

/**
 * The one card style: Surface1 on Ink, 1dp hairline, 16dp radius. Tappable cards get press
 * feedback and a soft spotlight that follows the finger.
 */
@Composable
fun ChittiSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color = Accent,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    var base = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Surface1)
        .border(1.dp, Hairline, RoundedCornerShape(16.dp))
    if (onClick != null) {
        base = base
            .pressScale(interaction, pressed = 0.985f)
            .spotlight(accent.copy(alpha = 0.10f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Column(modifier = base.padding(contentPadding), content = content)
}

/** A metric: big number that counts up, label underneath, tinted icon well. */
@Composable
fun MetricTile(
    label: String,
    value: Int,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    ChittiSurfaceCard(modifier = modifier, accent = tint, contentPadding = PaddingValues(Space.l)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(Space.m))
        CountUpText(
            target = value,
            style = MaterialTheme.typography.headlineMedium,
            color = TextHigh
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A standard list row: icon well, title, optional subtitle, trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Accent,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    var base = modifier.fillMaxWidth()
    if (onClick != null) {
        base = base
            .clip(RoundedCornerShape(12.dp))
            .pressScale(interaction, pressed = 0.99f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Row(
        modifier = base.padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(Space.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextHigh)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

/** Consistent empty state: quiet icon well, one line of title, one line of guidance. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .staggeredEntrance(0)
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface2)
                .border(1.dp, Hairline, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextLow, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(Space.l))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh, modifier = Modifier.staggeredEntrance(1))
        Spacer(Modifier.height(Space.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(2)
        )
    }
}

// ---------------------------------------------------------------- Buttons

@Composable
private fun BaseButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    border: Color?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(44.dp)
            .pressScale(interaction, pressed = 0.97f)
            .clip(RoundedCornerShape(12.dp))
            .background(container.copy(alpha = container.alpha * alpha))
            .then(if (border != null) Modifier.border(1.dp, border.copy(alpha = border.alpha * alpha), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = alpha), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = alpha)
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = true
) = BaseButton(text, onClick, Accent, OnAccent, null, modifier, enabled, icon, fill)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = false
) = BaseButton(text, onClick, Surface2, TextHigh, Hairline, modifier, enabled, icon, fill)

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = false
) = BaseButton(text, onClick, Rose.copy(alpha = 0.14f), Rose, Rose.copy(alpha = 0.35f), modifier, enabled, icon, fill)

/** Small status badge: tinted wash, tinted label. */
@Composable
fun StatusPill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.13f))
            .padding(horizontal = Space.s, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** A 6dp dot, for urgency and status marks. */
@Composable
fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/**
 * Segmented control. Tap a segment, or grab the thumb and drag it: it tracks the finger 1:1,
 * rubber-bands past either end, ticks as it crosses each segment, and on release lands on the
 * segment its momentum is heading for, carrying the finger's velocity into the settle.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val count = options.size.coerceAtLeast(1)
    val segmentPx = widthPx.toFloat() / count
    val thumbX = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var measured by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selectedIndex)

    LaunchedEffect(selectedIndex, segmentPx) {
        if (segmentPx <= 0f || dragging) return@LaunchedEffect
        val target = segmentPx * selectedIndex
        if (!measured) {
            thumbX.snapTo(target)
            measured = true
        } else {
            thumbX.animateTo(target, ChittiMotion.Settle)
        }
    }

    // While dragging, the labels follow the segment under the thumb, not the committed one.
    val hovered = if (segmentPx > 0f) ((thumbX.value + segmentPx / 2f) / segmentPx).toInt().coerceIn(0, count - 1) else selectedIndex
    val activeIndex = if (dragging) hovered else selectedIndex

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(count) {
                val tracker = VelocityTracker()
                var raw = 0f
                var lastHover = -1
                fun seg() = size.width.toFloat() / count
                detectHorizontalDragGestures(
                    onDragStart = { down ->
                        // Only the thumb is draggable, as on iOS; a drag that starts elsewhere is ignored.
                        dragging = down.x >= thumbX.value && down.x <= thumbX.value + seg()
                        if (dragging) {
                            tracker.resetTracking()
                            raw = thumbX.value
                            lastHover = ((raw + seg() / 2f) / seg()).toInt()
                            scope.launch { thumbX.stop() }
                        }
                    },
                    onHorizontalDrag = { change, delta ->
                        if (dragging) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val maxX = seg() * (count - 1)
                            raw += delta
                            val visual = when {
                                raw < 0f -> -rubberBand(-raw, seg())
                                raw > maxX -> maxX + rubberBand(raw - maxX, seg())
                                else -> raw
                            }
                            scope.launch { thumbX.snapTo(visual) }
                            val hover = ((visual + seg() / 2f) / seg()).toInt().coerceIn(0, count - 1)
                            if (hover != lastHover) {
                                lastHover = hover
                                haptics.tick()
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragging) {
                            val v = tracker.calculateVelocity().x
                            // A segmented control is short, so use the snappier deceleration rate.
                            val projected = thumbX.value + projectMomentum(v, decelerationRate = 0.99f)
                            val target = ((projected + seg() / 2f) / seg()).toInt().coerceIn(0, count - 1)
                            scope.launch { thumbX.animateTo(seg() * target, ChittiMotion.Settle, initialVelocity = v) }
                            dragging = false
                            if (target != selectedNow) select(target)
                        }
                    },
                    onDragCancel = {
                        if (dragging) {
                            dragging = false
                            scope.launch { thumbX.animateTo(seg() * selectedNow, ChittiMotion.Settle) }
                        }
                    }
                )
            }
    ) {
        if (widthPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX.value.roundToInt(), 0) }
                    .width(with(density) { segmentPx.toDp() })
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface3)
                    .border(1.dp, EdgeHighlight, RoundedCornerShape(8.dp))
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val selected = index == activeIndex
                val color by animateColorAsState(if (selected) TextHigh else TextMid, label = "segmentLabel")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                haptics.tick()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Labelled proportion bar, for category / status breakdowns. */
@Composable
fun MeterRow(
    label: String,
    value: Int,
    total: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val fraction = if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)
    // Fill from empty on first display, so the proportion is read as it arrives.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }
    val animated by animateFloatAsState(
        if (armed) fraction else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "meter"
    )
    Column(modifier = modifier.padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(tint)
            Spacer(Modifier.width(Space.s))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMid, modifier = Modifier.weight(1f))
            Text("$value", style = MaterialTheme.typography.labelLarge, color = TextHigh)
        }
        Spacer(Modifier.height(Space.s))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Surface3)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

/**
 * Compact tappable chip: tinted glyph, label, raised surface. Quick actions and suggestions use
 * this so every "do this now" affordance looks and responds the same way.
 */
@Composable
fun ChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Accent
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(36.dp)
            .pressScale(interaction, pressed = 0.95f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .spotlight(tint.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextHigh, maxLines = 1)
    }
}

/** The single text-field style. */
@Composable
fun ChittiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    supportingText: String? = null,
    label: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextLow) },
        label = if (label != null) {
            { Text(label, style = MaterialTheme.typography.bodySmall) }
        } else null,
        supportingText = if (supportingText != null) {
            { Text(supportingText, style = MaterialTheme.typography.labelSmall, color = TextLow) }
        } else null,
        leadingIcon = if (leadingIcon != null) {
            { Icon(leadingIcon, contentDescription = null, tint = TextLow, modifier = Modifier.size(18.dp)) }
        } else null,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Hairline,
            focusedTextColor = TextHigh,
            unfocusedTextColor = TextHigh,
            cursorColor = Accent,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextMid
        )
    )
}

/** Wraps content so uncoloured Text/Icon inherit the right ink on dark surfaces. */
@Composable
fun OnDarkContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides TextHigh, content = content)
}
