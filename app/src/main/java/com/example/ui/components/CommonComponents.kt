package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGlowPrimary
import com.example.ui.theme.ChemistryPurple
import com.example.ui.theme.CopperGlowAccent
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueSubtle
import com.example.ui.theme.GoldGlowHighlight
import com.example.ui.theme.MathOrange
import com.example.ui.theme.PhysicsCyan
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.TabularNumeralStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.viewmodel.AppScreen

/**
 * Restrained Obsidian & Emerald Glassmorphic Card (#191D20 surface) with a soft fading
 * radial emerald-mint ambient glow and subtle border.
 */
@Composable
fun GlowGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = SoftGreen,
    glowAlpha: Float = 0.16f,
    glowAlignment: Alignment = Alignment.BottomEnd,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            glowColor.copy(alpha = 0.32f),
            SlateCardBorder.copy(alpha = 0.9f),
            Color(0xFF161A1D)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(SlateCard)
            .border(1.dp, rimBrush, shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = when (glowAlignment) {
                Alignment.TopStart -> Offset(size.width * 0.15f, size.height * 0.18f)
                Alignment.TopEnd -> Offset(size.width * 0.85f, size.height * 0.2f)
                Alignment.BottomStart -> Offset(size.width * 0.18f, size.height * 0.85f)
                Alignment.Center -> Offset(size.width * 0.5f, size.height * 0.5f)
                else -> Offset(size.width * 0.82f, size.height * 0.78f)
            }
            val radius = (size.maxDimension * 0.78f).coerceAtLeast(100f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha),
                        CopperGlowAccent.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        content()
    }
}

/**
 * Segmented multi-block progress bar with fading emerald-mint gradient blocks.
 */
@Composable
fun SegmentedGlowProgressBar(
    progressFraction: Float,
    modifier: Modifier = Modifier,
    segmentCount: Int = 5
) {
    val clamped = progressFraction.coerceIn(0f, 1f)
    val activeSegments = (clamped * segmentCount).toInt().coerceAtLeast(if (clamped > 0f) 1 else 0)
    val segmentPalette = listOf(
        Color(0xFF065F46),
        Color(0xFF059669),
        CopperGlowAccent,
        SoftGreen,
        GoldGlowHighlight
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until segmentCount) {
            val isLit = i < activeSegments
            val segColor = if (isLit) {
                segmentPalette[i % segmentPalette.size]
            } else {
                SlateCardElevated
            }
            Box(
                modifier = Modifier
                    .weight(if (i == 1 || i == 2) 1.3f else 1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(segColor)
            )
        }
    }
}

@Composable
fun TopCommandBar(
    title: String,
    subtitle: String? = null,
    isDimmer: Boolean = false,
    onToggleDimmer: () -> Unit,
    onEnterFocusMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DeepNavyBg)
            .statusBarsPadding()
            .testTag("top_command_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SoftGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ElectricBlueSubtle)
                        .border(1.dp, SoftGreen.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .clickable { onEnterFocusMode() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("focus_mode_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            tint = SoftGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Focus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SoftGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onToggleDimmer,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SlateCardElevated)
                        .testTag("dimmer_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDimmer) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = "Toggle Eye-Strain Dimmer",
                        tint = if (isDimmer) WarmAmber else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SlateCardBorder)
        )
    }
}

/**
 * Minimalist Obsidian & Emerald Bottom Navigation Bar matching the blueprint:
 * Home · Backlog · Plan · Focus · Progress (+ More)
 * Preserves all existing testTags for compatibility.
 */
@Composable
fun BottomNavBar(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("bottom_nav_bar"),
        contentAlignment = Alignment.Center
    ) {
        val dockRimBrush = Brush.linearGradient(
            colors = listOf(
                SoftGreen.copy(alpha = 0.28f),
                SlateCardBorder,
                SlateCardBorder
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF516191D))
                .border(1.dp, dockRimBrush, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            NavPillItem(
                selected = currentScreen == AppScreen.DASHBOARD,
                label = "Home",
                icon = Icons.Default.Home,
                testTag = "nav_home",
                onClick = { onSelectScreen(AppScreen.DASHBOARD) }
            )

            // 2. Backlog
            NavPillItem(
                selected = currentScreen == AppScreen.TRACKER,
                label = "Backlog",
                icon = Icons.Default.Book,
                testTag = "nav_backlog",
                onClick = { onSelectScreen(AppScreen.TRACKER) }
            )

            // 3. Plan (80-Day Schedule)
            NavPillItem(
                selected = currentScreen == AppScreen.SCHEDULER,
                label = "Plan",
                icon = Icons.Default.CalendarMonth,
                testTag = "nav_schedule",
                onClick = { onSelectScreen(AppScreen.SCHEDULER) }
            )

            // 4. Focus
            NavPillItem(
                selected = currentScreen == AppScreen.FOCUS_MODE,
                label = "Focus",
                icon = Icons.Default.Timer,
                testTag = "nav_focus_center",
                onClick = { onSelectScreen(AppScreen.FOCUS_MODE) }
            )

            // 5. Progress (Analytics & Reports)
            NavPillItem(
                selected = currentScreen == AppScreen.ANALYTICS || currentScreen == AppScreen.TESTS,
                label = "Progress",
                icon = Icons.Default.BarChart,
                testTag = "nav_tests",
                onClick = { onSelectScreen(AppScreen.ANALYTICS) }
            )

            // 6. More (Revision, Tests, Mistakes, AI Mentor, Settings)
            val isMoreActive = currentScreen == AppScreen.MORE ||
                    currentScreen == AppScreen.MISTAKES ||
                    currentScreen == AppScreen.REST_SLEEP ||
                    currentScreen == AppScreen.AI_MENTOR ||
                    currentScreen == AppScreen.DIGITAL_WELLBEING ||
                    currentScreen == AppScreen.REVISION

            NavPillItem(
                selected = isMoreActive,
                label = "More",
                icon = Icons.Default.MoreHoriz,
                testTag = "nav_more",
                onClick = { onSelectScreen(AppScreen.MORE) }
            )
        }
    }
}

@Composable
private fun NavPillItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        colors = listOf(
                            SoftGreen.copy(alpha = 0.18f),
                            SlateCardElevated.copy(alpha = 0.85f)
                        )
                    )
                } else {
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
                }
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, SoftGreen.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) SoftGreen else TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) SoftGreen else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HubSegmentedHeader(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val hubScreens = listOf(
            Pair(AppScreen.MISTAKES, "Mistakes"),
            Pair(AppScreen.REST_SLEEP, "Screen & Rest"),
            Pair(AppScreen.AI_MENTOR, "AI Coach")
        )

        hubScreens.forEach { (screen, label) ->
            val isSelected = currentScreen == screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) SoftGreen else Color.Transparent)
                    .clickable { onSelectScreen(screen) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) DeepNavyBg else TextSecondary
                )
            }
        }
    }
}

@Composable
fun SubjectBadge(subject: String, modifier: Modifier = Modifier) {
    val (color, text) = when (subject.lowercase()) {
        "physics" -> Pair(PhysicsCyan, "PHY")
        "chemistry" -> Pair(ChemistryPurple, "CHEM")
        "mathematics", "maths", "math" -> Pair(MathOrange, "MATH")
        else -> Pair(ElectricBlue, subject.take(4).uppercase())
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (status) {
        "COMPLETED" -> Triple(SoftGreen.copy(alpha = 0.14f), SoftGreen, "Completed")
        "IN_PROGRESS" -> Triple(SoftGreen.copy(alpha = 0.14f), SoftGreen, "In Progress")
        "WEAK" -> Triple(WarmAmber.copy(alpha = 0.15f), WarmAmber, "Needs Revision")
        "NOT_ASSESSED" -> Triple(SlateCardElevated, TextMuted, "Not assessed")
        else -> Triple(SlateCardElevated, TextSecondary, "Upcoming")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricHudCard(
    title: String,
    value: String,
    subtitle: String? = null,
    accentColor: Color = SoftGreen,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    GlowGlassCard(
        modifier = modifier,
        glowColor = accentColor,
        glowAlpha = 0.16f,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.32f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = value,
                    style = TabularNumeralStyle,
                    fontSize = 17.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            letterSpacing = 0.3.sp
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SoftGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onActionClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
