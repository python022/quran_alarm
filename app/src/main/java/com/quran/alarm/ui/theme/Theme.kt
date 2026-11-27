package com.quran.alarm.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val QuranDarkColorScheme = darkColorScheme(
    primary = QuranColors.CtaBg,
    onPrimary = QuranColors.CtaText,
    primaryContainer = QuranColors.ActivePillBg,
    onPrimaryContainer = QuranColors.ActivePillText,
    secondary = QuranColors.GoldBadgeText,
    onSecondary = QuranColors.BgPrimary,
    secondaryContainer = QuranColors.GoldBadgeBg,
    onSecondaryContainer = QuranColors.GoldBadgeText,
    background = QuranColors.BgPrimary,
    onBackground = QuranColors.TextPrimary,
    surface = QuranColors.BgCard,
    onSurface = QuranColors.TextPrimary,
    surfaceVariant = QuranColors.BgElevated,
    onSurfaceVariant = QuranColors.TextSecondary,
    outline = QuranColors.BorderSubtle,
    error = QuranColors.DangerRed,
    onError = QuranColors.TextPrimary
)

@Composable
fun QuranAlarmTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QuranDarkColorScheme,
        typography = QuranTypography,
        content = content
    )
}

/**
 * Unified capsule pill used for every selectable option (tabs, days, presets, sounds).
 */
@Composable
fun QuranSelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    maxLines: Int = 1,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) QuranColors.ActivePillBg else QuranColors.BgElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) QuranColors.ActivePillBorder else QuranColors.BorderSubtle
        )
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) QuranColors.ActivePillText else QuranColors.TextSecondary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Unified Golden informational badge surface (#1B2229 bg + #2D3741 stroke).
 */
@Composable
fun GoldBadgeSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 10,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        color = QuranColors.GoldBadgeBg,
        border = BorderStroke(1.dp, QuranColors.GoldBadgeBorder)
    ) {
        content()
    }
}

@Composable
fun quranSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = QuranColors.CtaText,
    checkedTrackColor = QuranColors.CtaBg,
    checkedBorderColor = QuranColors.CtaBg,
    uncheckedThumbColor = QuranColors.TextMuted,
    uncheckedTrackColor = QuranColors.BgElevated,
    uncheckedBorderColor = QuranColors.BorderSubtle
)
