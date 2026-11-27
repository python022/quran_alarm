package com.quran.alarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.R
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import com.quran.alarm.ui.AlarmUiFormatters
import com.quran.alarm.ui.theme.GoldBadgeSurface
import com.quran.alarm.ui.theme.QuranColors
import com.quran.alarm.ui.theme.quranSwitchColors

/**
 * Unified Dark Slate + Emerald + Gold alarm card.
 * Keeps top row full-width so "ورد متسلسل" is never clipped, stretches Surah & Sound badges,
 * isolates vibration as an icon, and delegates deletion into the edit screen to prevent accidental taps.
 */
@Composable
fun AlarmCardItem(
    alarm: AlarmItem,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (alarm.isEnabled) QuranColors.BgCard else QuranColors.BgCard.copy(alpha = 0.65f),
        label = "alarmCardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (alarm.isEnabled) QuranColors.ActivePillBorder.copy(alpha = 0.35f) else QuranColors.BorderSubtle,
        label = "alarmCardBorder"
    )
    val hasActiveSnooze = alarm.isEnabled &&
        alarm.snoozeTriggerMillis != null &&
        alarm.snoozeTriggerMillis > System.currentTimeMillis()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: full-width time + AM/PM + wrap_content Wird type pill + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = AlarmUiFormatters.formatDigits12HourPadded(alarm.hour, alarm.minute),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = if (alarm.isEnabled) QuranColors.TextPrimary else QuranColors.TextMuted
                        )
                    }

                    Text(
                        text = AlarmUiFormatters.getPeriodLabel(alarm.hour),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (alarm.isEnabled) QuranColors.TextSecondary else QuranColors.TextMuted
                    )

                    Surface(
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        shape = CircleShape,
                        color = if (alarm.isSequential) QuranColors.ActivePillBg else QuranColors.BgElevated,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (alarm.isSequential) QuranColors.ActivePillBorder else QuranColors.BorderSubtle
                        )
                    ) {
                        Text(
                            text = if (alarm.isSequential) "ورد متسلسل" else "ورد ثابت",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            color = if (alarm.isSequential) QuranColors.ActivePillText else QuranColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = quranSwitchColors()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Repeat days & label (+ optional active snooze badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${AlarmUiFormatters.formatRepeatDays(alarm.repeatDays)} • ${alarm.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (alarm.isEnabled) QuranColors.TextSecondary else QuranColors.TextMuted,
                    modifier = Modifier.weight(1f)
                )

                if (hasActiveSnooze) {
                    Surface(
                        shape = CircleShape,
                        color = QuranColors.WarningOrangeBg,
                        border = BorderStroke(1.dp, QuranColors.WarningOrange.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "غفوة نشطة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            color = QuranColors.WarningOrange,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Full-width Golden Surah/Wird Badge
            GoldBadgeSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "📖 ${AlarmUiFormatters.formatWirdCardSummary(alarm)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (alarm.isEnabled) QuranColors.GoldBadgeText else QuranColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Full-width Sound Badge + Standalone Vibration Icon (consistent single-line height across all cards)
            val rawSoundTitle = alarm.soundTitle ?: AlarmSoundPresets.getTitleForUri(alarm.soundUri)
            val compactSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(rawSoundTitle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoldBadgeSurface(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔔 $compactSoundTitle (${alarm.volumeLevel}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (alarm.isEnabled) QuranColors.GoldBadgeText else QuranColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (alarm.isVibrationEnabled) QuranColors.GoldBadgeBg else QuranColors.BgElevated,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (alarm.isVibrationEnabled) QuranColors.GoldBadgeBorder else QuranColors.BorderSubtle
                    ),
                    modifier = Modifier.size(width = 36.dp, height = 30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_vibration),
                            contentDescription = if (alarm.isVibrationEnabled) "الاهتزاز مفعل" else "الاهتزاز متوقف",
                            tint = if (alarm.isVibrationEnabled && alarm.isEnabled) {
                                QuranColors.GoldBadgeText
                            } else {
                                QuranColors.TextMuted
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
