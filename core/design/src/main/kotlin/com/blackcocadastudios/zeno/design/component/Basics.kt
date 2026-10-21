package com.blackcocadastudios.zeno.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoMotion

/** A line as fine as the screen allows: shoji frames, not walls. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Zeno.colors.line) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color),
    )
}

/** Page load progress: a single stroke of the accent that fades once the page settles. */
@Composable
fun ProgressLine(
    progress: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Zeno.colors.accent,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(ZenoMotion.STANDARD, easing = ZenoMotion.Calm),
        label = "progress",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(ZenoMotion.GENTLE, easing = ZenoMotion.Calm),
        label = "progressAlpha",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        if (alpha <= 0f) return@Canvas
        val width = size.width * animated
        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
        drawRect(color.copy(alpha = alpha * color.alpha), topLeft = Offset(left, 0f), size = Size(width, size.height))
    }
}

/** Number of open tabs in a small square frame, like a seal. Past 99 it is simply ∞. */
@Composable
fun TabCounter(count: Int, modifier: Modifier = Modifier, color: Color = Zeno.colors.ink) {
    val text = if (count > 99) "∞" else count.toString()
    Box(
        modifier
            .size(21.dp)
            .border(1.5.dp, color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Zeno.type.caption.copy(
                fontWeight = FontWeight.Medium,
                fontSize = if (text.length > 1) 10.sp else 12.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.em,
                textAlign = TextAlign.Center,
                color = color,
            ),
            maxLines = 1,
        )
    }
}

/** 48dp touch target with a 24dp line icon and a soft unbounded ripple. */
@Composable
fun ZenoIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Zeno.colors.ink,
) {
    Box(
        modifier = modifier
            .size(Ma.touch)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.32f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** A quiet label above a group of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Zeno.type.caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08.em),
        color = Zeno.colors.inkSoft,
        modifier = modifier.padding(start = Ma.xl, end = Ma.xl, top = Ma.xl, bottom = Ma.s),
    )
}

/** The row every list in Zeno is made of. */
@Composable
fun ZenoListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Zeno.colors.ink,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = 56.dp)
            .padding(horizontal = Ma.xl, vertical = Ma.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Ma.l))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = Zeno.colors.inkSoft, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Ma.l))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = Zeno.type.body,
                color = if (enabled) titleColor else Zeno.colors.inkFaint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Zeno.type.bodySmall,
                    color = if (enabled) Zeno.colors.inkSoft else Zeno.colors.inkFaint,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Ma.m))
            trailing()
        }
    }
}

@Composable
fun ZenoSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val colors = Zeno.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.paperRaised,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.inkFaint,
            uncheckedTrackColor = colors.paperSunken,
            uncheckedBorderColor = colors.line,
        ),
    )
}

/** A switch row: the whole row toggles, not only the switch. */
@Composable
fun ZenoSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ZenoListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = { ZenoSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * The one text button: a pill. [filled] is the single main action of a place
 * (accent ground); outlined is everything else.
 */
@Composable
fun ZenoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = Zeno.colors
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .heightIn(min = 40.dp)
            .then(
                if (filled) {
                    Modifier.background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f), shape)
                } else {
                    Modifier.border(1.dp, if (enabled) colors.accent else colors.line, shape)
                },
            )
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Ma.xl, vertical = Ma.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Zeno.type.label,
            color = when {
                filled -> colors.onAccent
                enabled -> colors.accentInk
                else -> colors.inkFaint
            },
            maxLines = 1,
        )
    }
}

/** Something absent, said quietly: a faint ensō and one line. */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, body: String? = null) {
    Column(
        modifier = modifier.padding(horizontal = Ma.xxl, vertical = Ma.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Enso(Modifier.size(56.dp), color = Zeno.colors.accent.copy(alpha = 0.55f))
        Spacer(Modifier.height(Ma.xl))
        Text(title, style = Zeno.type.mincho, color = Zeno.colors.ink, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(Ma.s))
            Text(body, style = Zeno.type.bodySmall, color = Zeno.colors.inkSoft, textAlign = TextAlign.Center)
        }
    }
}
