package com.quran.alarm.ui.components

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import com.quran.alarm.alarm.domain.model.KhatmaForecast
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.model.WirdSelectionMode
import com.quran.alarm.ui.AlarmUiFormatters
import com.quran.alarm.ui.theme.GoldBadgeSurface
import com.quran.alarm.ui.theme.QuranColors
import com.quran.alarm.ui.theme.QuranSelectablePill
import com.quran.alarm.ui.theme.quranSwitchColors
import java.util.Calendar
import kotlin.math.ceil

@Composable
internal fun WirdConfigurationSection(
    selectionMode: WirdSelectionMode,
    selectedStartSurah: Int,
    selectedEndSurah: Int,
    selectedStartHizb: Int,
    selectedEndHizb: Int,
    selectedStartJuz: Int,
    selectedEndJuz: Int,
    startPage: Int,
    endPage: Int,
    useAyahCount: Boolean,
    startAyahInput: Int,
    dailyAyahCountInput: Int,
    isAyahModeActive: Boolean,
    isSequential: Boolean,
    calculatedPageCount: Int,
    forecast: KhatmaForecast,
    onSelectMode: (WirdSelectionMode) -> Unit,
    onStartSurahSelected: (Int) -> Unit,
    onEndSurahSelected: (Int) -> Unit,
    onOpenStartSurahPicker: () -> Unit,
    onOpenEndSurahPicker: () -> Unit,
    onOpenStartPageInput: () -> Unit,
    onOpenEndPageInput: () -> Unit,
    onStartHizbSelected: (Int) -> Unit,
    onEndHizbSelected: (Int) -> Unit,
    onStartJuzSelected: (Int) -> Unit,
    onEndJuzSelected: (Int) -> Unit,
    onStartPageSelected: (Int) -> Unit,
    onEndPageSelected: (Int) -> Unit,
    onQuickPortionSelected: (Int) -> Unit,
    onToggleAyahCount: (Boolean) -> Unit,
    onStartAyahChanged: (Int) -> Unit,
    onDailyAyahCountChanged: (Int) -> Unit,
    onSequentialChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = QuranColors.BgCard),
        border = BorderStroke(1.dp, QuranColors.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "📖 تحديد الورد القرآني",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = QuranColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    listOf(WirdSelectionMode.SURAHS, WirdSelectionMode.AHZAB),
                    listOf(WirdSelectionMode.AJZAA, WirdSelectionMode.PAGES)
                ).forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowModes.forEach { mode ->
                            QuranSelectablePill(
                                text = mode.titleArabic,
                                selected = selectionMode == mode,
                                onClick = { onSelectMode(mode) },
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Elevated inner wheel container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = QuranColors.BgElevated),
                border = BorderStroke(1.dp, QuranColors.BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "البدء",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = QuranColors.TextPrimary
                            )
                            if (selectionMode == WirdSelectionMode.SURAHS) {
                                ProminentPickerActionChip(
                                    label = "بحث بالاسم",
                                    leadingSearchIcon = true,
                                    onClick = onOpenStartSurahPicker
                                )
                            } else if (selectionMode == WirdSelectionMode.PAGES) {
                                ProminentPickerActionChip(
                                    label = "إدخال رقم",
                                    leadingSearchIcon = false,
                                    onClick = onOpenStartPageInput
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "الانتهاء",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = QuranColors.ActivePillText
                            )
                            if (selectionMode == WirdSelectionMode.SURAHS) {
                                ProminentPickerActionChip(
                                    label = "بحث بالاسم",
                                    leadingSearchIcon = true,
                                    onClick = onOpenEndSurahPicker
                                )
                            } else if (selectionMode == WirdSelectionMode.PAGES) {
                                ProminentPickerActionChip(
                                    label = "إدخال رقم",
                                    leadingSearchIcon = false,
                                    onClick = onOpenEndPageInput
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (selectionMode) {
                        WirdSelectionMode.SURAHS -> {
                            val allSurahs = remember { (1..114).toList() }
                            RangeWheelPickerRow(
                                items = allSurahs,
                                startSelected = selectedStartSurah,
                                endSelected = selectedEndSurah,
                                onStartSelected = onStartSurahSelected,
                                onEndSelected = onEndSurahSelected,
                                startLabel = {
                                    val s = QuranStructure.getSurahByNumber(it)
                                    "${s.nameArabic} (ص ${s.startPage})"
                                },
                                endLabel = {
                                    val s = QuranStructure.getSurahByNumber(it)
                                    "${s.nameArabic} (ص ${s.endPage})"
                                }
                            )
                        }
                        WirdSelectionMode.AHZAB -> {
                            val allAhzab = remember { (1..60).toList() }
                            RangeWheelPickerRow(
                                items = allAhzab,
                                startSelected = selectedStartHizb,
                                endSelected = selectedEndHizb,
                                onStartSelected = onStartHizbSelected,
                                onEndSelected = onEndHizbSelected,
                                startLabel = { "حزب $it (ص ${QuranStructure.getStartPageForHizb(it)})" },
                                endLabel = { "حزب $it (ص ${QuranStructure.getEndPageForHizb(it)})" }
                            )
                        }
                        WirdSelectionMode.AJZAA -> {
                            val allAjzaa = remember { (1..30).toList() }
                            RangeWheelPickerRow(
                                items = allAjzaa,
                                startSelected = selectedStartJuz,
                                endSelected = selectedEndJuz,
                                onStartSelected = onStartJuzSelected,
                                onEndSelected = onEndJuzSelected,
                                startLabel = { "جزء $it (ص ${QuranStructure.getStartPageForJuz(it)})" },
                                endLabel = { "جزء $it (ص ${QuranStructure.getEndPageForJuz(it)})" }
                            )
                        }
                        WirdSelectionMode.PAGES -> {
                            val allPages = remember { (1..604).toList() }
                            RangeWheelPickerRow(
                                items = allPages,
                                startSelected = startPage,
                                endSelected = endPage,
                                onStartSelected = onStartPageSelected,
                                onEndSelected = onEndPageSelected,
                                fontSize = 18.sp,
                                startLabel = { "ص $it" },
                                endLabel = { "ص $it" }
                            )
                        }
                    }

                    if (selectionMode == WirdSelectionMode.SURAHS && selectedStartSurah == selectedEndSurah) {
                        val sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
                        val safeStartAyah = startAyahInput.coerceIn(1, sameSurah.ayahCount)
                        val safeDailyAyah = dailyAyahCountInput.coerceIn(1, sameSurah.ayahCount)
                        val allAyahs = remember(sameSurah.number) { (1..sameSurah.ayahCount).toList() }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuranSelectablePill(
                                text = "السورة كاملة (${AlarmUiFormatters.formatAyahsCount(sameSurah.ayahCount)})",
                                selected = !useAyahCount,
                                onClick = { onToggleAyahCount(false) },
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            QuranSelectablePill(
                                text = "تحديد آيات يومياً",
                                selected = useAyahCount,
                                onClick = { onToggleAyahCount(true) },
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (useAyahCount) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "آية البدء",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = QuranColors.TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "عدد الآيات يومياً",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = QuranColors.ActivePillText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VerticalWheelPicker(
                                    items = allAyahs,
                                    selectedItem = safeStartAyah,
                                    onItemSelected = onStartAyahChanged,
                                    isInfinite = false,
                                    itemHeight = 40.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = { "آية $it" },
                                    modifier = Modifier.weight(1f)
                                )

                                VerticalWheelPicker(
                                    items = allAyahs,
                                    selectedItem = safeDailyAyah,
                                    onItemSelected = onDailyAyahCountChanged,
                                    isInfinite = false,
                                    itemHeight = 40.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = { AlarmUiFormatters.formatAyahsCount(it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Quick portion shortcut buttons below the wheel (+1 صفحة، +2، +5، ربع، حزب)
                    Spacer(modifier = Modifier.height(10.dp))
                    QuickWirdPortionRow(
                        startPage = startPage,
                        endPage = endPage,
                        onSelectTargetEndPage = onQuickPortionSelected
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instant Live Wird Preview Box (total pages, estimated time, and large-wird warning)
            WirdLivePreviewBox(
                isAyahModeActive = isAyahModeActive,
                selectedStartSurah = selectedStartSurah,
                startAyahInput = startAyahInput,
                dailyAyahCountInput = dailyAyahCountInput,
                startPage = startPage,
                endPage = endPage,
                calculatedPageCount = calculatedPageCount,
                forecast = forecast
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSequential) "الورد المتسلسل التلقائي" else "ورد ثابت محدد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QuranColors.TextPrimary
                    )
                    Text(
                        text = if (isSequential) {
                            "يتقدم تلقائياً كل يوم للصفحات التالية لختم المصحف"
                        } else {
                            "قراءة نفس السور أو الصفحات يومياً دون تقدم"
                        },
                        fontSize = 12.sp,
                        color = QuranColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isSequential,
                    onCheckedChange = onSequentialChanged,
                    colors = quranSwitchColors()
                )
            }
        }
    }
}

@Composable
private fun ProminentPickerActionChip(
    label: String,
    leadingSearchIcon: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = QuranColors.ActivePillBg,
        border = BorderStroke(1.dp, QuranColors.ActivePillBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingSearchIcon) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = QuranColors.ActivePillText,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = QuranColors.ActivePillText,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickWirdPortionRow(
    startPage: Int,
    endPage: Int,
    onSelectTargetEndPage: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val safeStart = startPage.coerceIn(1, QuranStructure.TOTAL_PAGES)
        val safeEnd = endPage.coerceIn(safeStart, QuranStructure.TOTAL_PAGES)
        val currentLen = safeEnd - safeStart + 1

        QuranSelectablePill(
            text = "+1 صفحة",
            selected = false,
            onClick = { onSelectTargetEndPage((safeEnd + 1).coerceAtMost(QuranStructure.TOTAL_PAGES)) },
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        )
        QuranSelectablePill(
            text = "+2",
            selected = false,
            onClick = { onSelectTargetEndPage((safeEnd + 2).coerceAtMost(QuranStructure.TOTAL_PAGES)) },
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        )
        QuranSelectablePill(
            text = "+5",
            selected = false,
            onClick = { onSelectTargetEndPage((safeEnd + 5).coerceAtMost(QuranStructure.TOTAL_PAGES)) },
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        )
        QuranSelectablePill(
            text = "ربع",
            selected = currentLen == 3,
            onClick = { onSelectTargetEndPage((safeStart + 2).coerceAtMost(QuranStructure.TOTAL_PAGES)) },
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        )
        val hizbAtStart = QuranStructure.getHizbAtPage(safeStart)
        val hizbEndTarget = if (safeStart == QuranStructure.getStartPageForHizb(hizbAtStart)) {
            QuranStructure.getEndPageForHizb(hizbAtStart)
        } else {
            (safeStart + 9).coerceAtMost(QuranStructure.TOTAL_PAGES)
        }
        QuranSelectablePill(
            text = "حزب",
            selected = safeEnd == hizbEndTarget && currentLen >= 8,
            onClick = { onSelectTargetEndPage(hizbEndTarget) },
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        )
        if (safeEnd > safeStart) {
            QuranSelectablePill(
                text = "صفحة واحدة",
                selected = false,
                onClick = { onSelectTargetEndPage(safeStart) },
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun WirdLivePreviewBox(
    isAyahModeActive: Boolean,
    selectedStartSurah: Int,
    startAyahInput: Int,
    dailyAyahCountInput: Int,
    startPage: Int,
    endPage: Int,
    calculatedPageCount: Int,
    forecast: KhatmaForecast
) {
    val isVeryLarge = calculatedPageCount > 20
    val isModeratelyLarge = calculatedPageCount in 11..20

    val borderColor = when {
        isVeryLarge -> QuranColors.DangerRed.copy(alpha = 0.7f)
        isModeratelyLarge -> QuranColors.WarningOrange.copy(alpha = 0.7f)
        else -> QuranColors.GoldBadgeBorder
    }
    val boxColor = when {
        isVeryLarge -> QuranColors.DangerRedBg
        isModeratelyLarge -> QuranColors.WarningOrangeBg
        else -> QuranColors.GoldBadgeBg
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = boxColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAyahModeActive) {
                val sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
                val safeStartAyah = startAyahInput.coerceIn(1, sameSurah.ayahCount)
                val safeDailyAyah = dailyAyahCountInput.coerceIn(1, sameSurah.ayahCount)
                val safeEndAyah = QuranStructure.computeEndAyah(selectedStartSurah, safeStartAyah, safeDailyAyah)
                    ?: safeStartAyah
                val remainingAyahs = sameSurah.ayahCount - safeStartAyah + 1
                val daysToFinish = ceil(remainingAyahs.toDouble() / safeDailyAyah.toDouble()).toInt()
                val ayahForecastText = if (remainingAyahs <= safeDailyAyah) {
                    "اليوم إتمام سورة ${sameSurah.nameArabic} بإذن الله!"
                } else {
                    "إتمام سورة ${sameSurah.nameArabic} خلال ${AlarmUiFormatters.formatDaysCount(daysToFinish)} (${AlarmUiFormatters.formatAyahsCount(remainingAyahs)} متبقية)"
                }
                val estimatedTime = AlarmUiFormatters.formatEstimatedReadingTime(
                    pageCount = calculatedPageCount,
                    ayahCount = safeDailyAyah,
                    surahTotalAyahs = sameSurah.ayahCount
                )
                val pagesLabel = if (startPage == endPage) {
                    "صفحة $startPage (صفحة واحدة)"
                } else {
                    "ص $startPage-$endPage (${AlarmUiFormatters.formatPagesCount(calculatedPageCount)})"
                }

                Text(
                    text = "معاينة الورد: سورة ${sameSurah.nameArabic} • من آية $safeStartAyah إلى آية $safeEndAyah (${AlarmUiFormatters.formatAyahsCount(safeDailyAyah)} • $pagesLabel)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QuranColors.TextPrimary,
                    lineHeight = 19.sp
                )
                Text(
                    text = "⏱ الوقت التقريبي للقراءة: $estimatedTime",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuranColors.ActivePillText
                )
                Text(
                    text = ayahForecastText,
                    fontSize = 12.sp,
                    color = QuranColors.GoldBadgeText,
                    lineHeight = 18.sp
                )
            } else {
                val rangeSummary = AlarmUiFormatters.formatPageRangeSummary(startPage, endPage)
                val estimatedTime = AlarmUiFormatters.formatEstimatedReadingTime(calculatedPageCount)

                Text(
                    text = "معاينة الورد: $rangeSummary",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QuranColors.TextPrimary,
                    lineHeight = 19.sp
                )
                Text(
                    text = "📊 إجمالي الورد: ${AlarmUiFormatters.formatPagesCount(calculatedPageCount)} • ⏱ الوقت التقريبي: $estimatedTime",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuranColors.ActivePillText,
                    lineHeight = 18.sp
                )
                Text(
                    text = forecast.forecastSummary,
                    fontSize = 12.sp,
                    color = QuranColors.GoldBadgeText,
                    lineHeight = 18.sp
                )
            }

            if (isVeryLarge) {
                Text(
                    text = "⚠ تنبيه: الورد كبير جداً (${AlarmUiFormatters.formatPagesCount(calculatedPageCount)})، وقد يتطلب وقتاً طويلاً لإتمامه قبل إغلاق المنبه.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QuranColors.DangerRed,
                    lineHeight = 18.sp
                )
            } else if (isModeratelyLarge) {
                Text(
                    text = "⚠ ملاحظة: حجم الورد كبير نسبياً (${AlarmUiFormatters.formatPagesCount(calculatedPageCount)}).",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuranColors.WarningOrange,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
internal fun CollapsibleSettingCard(
    title: String,
    summary: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = QuranColors.BgCard),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) QuranColors.ActivePillBorder.copy(alpha = 0.45f) else QuranColors.BorderSubtle
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QuranColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = QuranColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "طي القسم" else "توسيع القسم",
                    tint = QuranColors.TextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmAudioAndSoundSection(
    volumeLevel: Float,
    isVibrate: Boolean,
    selectedSoundUri: String?,
    selectedSoundTitle: String,
    isPlayingPreview: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onTogglePreview: () -> Unit,
    onSelectPreset: (uriString: String?, title: String) -> Unit,
    onLaunchDocumentPicker: () -> Unit,
    onLaunchRingtonePicker: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "درجة الصوت: ${volumeLevel.toInt()}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = QuranColors.TextSecondary
        )
        Slider(
            value = volumeLevel,
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChangeFinished,
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(
                thumbColor = QuranColors.CtaBg,
                activeTrackColor = QuranColors.CtaBg,
                inactiveTrackColor = QuranColors.BgElevated
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الاهتزاز المتواصل",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = QuranColors.TextPrimary
            )
            Switch(
                checked = isVibrate,
                onCheckedChange = onVibrateChange,
                colors = quranSwitchColors()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Elevated sound selection container (#16202A)
        Surface(
            color = QuranColors.BgElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, QuranColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "النغمة الحالية",
                            fontSize = 11.sp,
                            color = QuranColors.TextSecondary
                        )
                        Text(
                            text = AlarmUiFormatters.formatCompactSoundTitle(selectedSoundTitle),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = QuranColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onTogglePreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayingPreview) QuranColors.DangerRed else QuranColors.CtaBg,
                            contentColor = if (isPlayingPreview) QuranColors.TextPrimary else QuranColors.CtaText
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (isPlayingPreview) "إيقاف ⏹" else "استماع ▶",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "النغمات المتاحة:",
                    fontSize = 12.sp,
                    color = QuranColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlarmSoundPresets.PRESET_OPTIONS.forEach { preset ->
                        val isSelected = selectedSoundUri == preset.uriString ||
                            (selectedSoundUri == null && preset.uriString == null) ||
                            (selectedSoundUri == AlarmSoundPresets.URI_DEFAULT && preset.uriString == null)
                        QuranSelectablePill(
                            text = preset.title,
                            selected = isSelected,
                            onClick = { onSelectPreset(preset.uriString, preset.title) },
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onLaunchDocumentPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuranColors.TextPrimary)
                    ) {
                        Text(
                            text = "ملف من الجهاز",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = onLaunchRingtonePicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuranColors.TextPrimary)
                    ) {
                        Text(
                            text = "نغمات الهاتف",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepeatSelectionContent(
    daysList: List<Pair<Int, String>>,
    repeatDays: Set<Int>,
    onRepeatDaysChanged: (Set<Int>) -> Unit
) {
    val defaultWorkdays = remember { AlarmUiFormatters.getDefaultWorkdays() }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuranSelectablePill(
            text = "كل يوم",
            selected = repeatDays.size == 7,
            onClick = {
                onRepeatDaysChanged(
                    setOf(
                        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                    )
                )
            },
            fontSize = 12.sp
        )
        QuranSelectablePill(
            text = "وسط أيام الأسبوع",
            selected = repeatDays == defaultWorkdays,
            onClick = { onRepeatDaysChanged(defaultWorkdays) },
            fontSize = 12.sp
        )
        QuranSelectablePill(
            text = "مرة واحدة",
            selected = repeatDays.isEmpty(),
            onClick = { onRepeatDaysChanged(emptySet()) },
            fontSize = 12.sp
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    val row1Days = remember(daysList) { daysList.take(4) }
    val row2Days = remember(daysList) { daysList.drop(4) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(row1Days, row2Days).forEach { rowDays ->
            DayOfWeekRow(
                days = rowDays,
                repeatDays = repeatDays,
                onToggleDay = { dayConst, selected ->
                    onRepeatDaysChanged(if (selected) repeatDays - dayConst else repeatDays + dayConst)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelSelectionContent(
    label: String,
    onLabelChanged: (String) -> Unit,
    onPresetSelected: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "صلاة الفجر والورد القرآني",
            "قيام الليل والورد القرآني",
            "سورة الملك قبل النوم",
            "سورة الكهف الجمعة"
        ).forEach { preset ->
            QuranSelectablePill(
                text = preset,
                selected = label == preset,
                onClick = { onPresetSelected(preset) },
                fontSize = 12.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = label,
        onValueChange = { onLabelChanged(it.take(AlarmItem.MAX_LABEL_LENGTH)) },
        label = {
            Text(
                text = "الوصف المخصص",
                color = QuranColors.TextSecondary,
                fontSize = 12.sp
            )
        },
        placeholder = {
            Text(
                text = "أدخل تسمية المنبه...",
                color = QuranColors.TextMuted,
                fontSize = 13.sp
            )
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = QuranColors.TextPrimary,
            unfocusedTextColor = QuranColors.TextPrimary,
            focusedContainerColor = QuranColors.BgElevated,
            unfocusedContainerColor = QuranColors.BgElevated,
            focusedBorderColor = QuranColors.ActivePillBorder,
            unfocusedBorderColor = QuranColors.BorderSubtle,
            focusedLabelColor = QuranColors.ActivePillText,
            unfocusedLabelColor = QuranColors.TextSecondary,
            cursorColor = QuranColors.ActivePillBorder
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
internal fun DayOfWeekRow(
    days: List<Pair<Int, String>>,
    repeatDays: Set<Int>,
    onToggleDay: (Int, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { (dayConst, dayName) ->
            val selected = repeatDays.contains(dayConst)
            QuranSelectablePill(
                text = dayName,
                selected = selected,
                onClick = { onToggleDay(dayConst, selected) },
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun PageDirectInputDialog(
    title: String,
    initialPage: Int,
    minAllowedPage: Int,
    onDismiss: () -> Unit,
    onConfirmPage: (Int) -> Unit
) {
    var inputText by remember(initialPage) { mutableStateOf(initialPage.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuranColors.BgCard,
        title = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = title,
                    color = QuranColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "أدخل رقم الصفحة المطلوبة (من $minAllowedPage إلى ${QuranStructure.TOTAL_PAGES}):",
                        color = QuranColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { raw ->
                            inputText = raw.filter { it.isDigit() }.take(3)
                            errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "مثال: 293",
                                color = QuranColors.TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = QuranColors.TextPrimary,
                            unfocusedTextColor = QuranColors.TextPrimary,
                            focusedContainerColor = QuranColors.BgElevated,
                            unfocusedContainerColor = QuranColors.BgElevated,
                            focusedBorderColor = QuranColors.ActivePillBorder,
                            unfocusedBorderColor = QuranColors.BorderSubtle,
                            cursorColor = QuranColors.ActivePillBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = QuranColors.DangerRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = inputText.toIntOrNull()
                    when {
                        parsed == null || parsed !in 1..QuranStructure.TOTAL_PAGES -> {
                            errorMessage = "يرجى إدخال رقم صفحة صحيح بين 1 و ${QuranStructure.TOTAL_PAGES}"
                        }
                        parsed < minAllowedPage -> {
                            errorMessage = "يجب أن تكون صفحة النهاية ($parsed) مساوية أو بعد صفحة البداية ($minAllowedPage)"
                        }
                        else -> {
                            onConfirmPage(parsed)
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuranColors.CtaBg,
                    contentColor = QuranColors.CtaText
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تطبيق", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = QuranColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun <T> RangeWheelPickerRow(
    items: List<T>,
    startSelected: T,
    endSelected: T,
    onStartSelected: (T) -> Unit,
    onEndSelected: (T) -> Unit,
    startLabel: (T) -> String,
    endLabel: (T) -> String,
    fontSize: TextUnit = 13.sp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalWheelPicker(
            items = items,
            selectedItem = startSelected,
            onItemSelected = onStartSelected,
            isInfinite = false,
            itemHeight = 44.dp,
            fontSize = fontSize,
            showDividers = true,
            itemLabel = startLabel,
            modifier = Modifier.weight(1f)
        )
        VerticalWheelPicker(
            items = items,
            selectedItem = endSelected,
            onItemSelected = onEndSelected,
            isInfinite = false,
            itemHeight = 44.dp,
            fontSize = fontSize,
            showDividers = true,
            itemLabel = endLabel,
            modifier = Modifier.weight(1f)
        )
    }
}

internal fun buildRingtonePickerIntent(selectedSoundUri: String?): Intent {
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        val currentUri = selectedSoundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
    }
}
