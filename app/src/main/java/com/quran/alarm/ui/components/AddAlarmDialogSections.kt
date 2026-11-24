package com.quran.alarm.ui.components

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import com.quran.alarm.alarm.domain.model.KhatmaForecast
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.model.WirdSelectionMode
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
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
    onStartHizbSelected: (Int) -> Unit,
    onEndHizbSelected: (Int) -> Unit,
    onStartJuzSelected: (Int) -> Unit,
    onEndJuzSelected: (Int) -> Unit,
    onStartPageSelected: (Int) -> Unit,
    onEndPageSelected: (Int) -> Unit,
    onToggleAyahCount: (Boolean) -> Unit,
    onStartAyahChanged: (Int) -> Unit,
    onDailyAyahCountChanged: (Int) -> Unit,
    onSequentialChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "📖 تحديد الورد القرآني",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF38BDF8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            FilterChip(
                                selected = selectionMode == mode,
                                onClick = { onSelectMode(mode) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        text = mode.titleArabic,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectionMode == mode) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFF10B981),
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "البدء",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            if (selectionMode == WirdSelectionMode.SURAHS) {
                                Text(
                                    text = "🔍 بحث بالاسم",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .clickable(onClick = onOpenStartSurahPicker)
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "الانتهاء",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            if (selectionMode == WirdSelectionMode.SURAHS) {
                                Text(
                                    text = "🔍 بحث بالاسم",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .clickable(onClick = onOpenEndSurahPicker)
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (selectionMode) {
                            WirdSelectionMode.SURAHS -> {
                                val allSurahs = remember { (1..114).toList() }
                                VerticalWheelPicker(
                                    items = allSurahs,
                                    selectedItem = selectedStartSurah,
                                    onItemSelected = onStartSurahSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = {
                                        val s = QuranStructure.getSurahByNumber(it)
                                        "${s.nameArabic} (${s.startPage})"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalWheelPicker(
                                    items = allSurahs,
                                    selectedItem = selectedEndSurah,
                                    onItemSelected = onEndSurahSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = {
                                        val s = QuranStructure.getSurahByNumber(it)
                                        "${s.nameArabic} (${s.endPage})"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            WirdSelectionMode.AHZAB -> {
                                val allAhzab = remember { (1..60).toList() }
                                VerticalWheelPicker(
                                    items = allAhzab,
                                    selectedItem = selectedStartHizb,
                                    onItemSelected = onStartHizbSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = { "حزب $it (ص ${QuranStructure.getStartPageForHizb(it)})" },
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalWheelPicker(
                                    items = allAhzab,
                                    selectedItem = selectedEndHizb,
                                    onItemSelected = onEndHizbSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = { "حزب $it (ص ${QuranStructure.getEndPageForHizb(it)})" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            WirdSelectionMode.AJZAA -> {
                                val allAjzaa = remember { (1..30).toList() }
                                VerticalWheelPicker(
                                    items = allAjzaa,
                                    selectedItem = selectedStartJuz,
                                    onItemSelected = onStartJuzSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = { "جزء $it (ص ${QuranStructure.getStartPageForJuz(it)})" },
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalWheelPicker(
                                    items = allAjzaa,
                                    selectedItem = selectedEndJuz,
                                    onItemSelected = onEndJuzSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 13.sp,
                                    showDividers = true,
                                    itemLabel = { "جزء $it (ص ${QuranStructure.getEndPageForJuz(it)})" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            WirdSelectionMode.PAGES -> {
                                val allPages = remember { (1..604).toList() }
                                VerticalWheelPicker(
                                    items = allPages,
                                    selectedItem = startPage,
                                    onItemSelected = onStartPageSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 16.sp,
                                    showDividers = true,
                                    itemLabel = { "ص $it" },
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalWheelPicker(
                                    items = allPages,
                                    selectedItem = endPage,
                                    onItemSelected = onEndPageSelected,
                                    isInfinite = false,
                                    itemHeight = 44.dp,
                                    fontSize = 16.sp,
                                    showDividers = true,
                                    itemLabel = { "ص $it" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
                            FilterChip(
                                selected = !useAyahCount,
                                onClick = { onToggleAyahCount(false) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        text = "السورة كاملة (${sameSurah.ayahCount} آية)",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFF10B981),
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                            FilterChip(
                                selected = useAyahCount,
                                onClick = { onToggleAyahCount(true) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        text = "تحديد آيات يومياً",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFF10B981),
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }

                        if (useAyahCount) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "آية البدء",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "عدد الآيات يومياً",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
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
                                    itemLabel = { "$it آية" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isAyahModeActive) {
                val sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
                val safeStartAyah = startAyahInput.coerceIn(1, sameSurah.ayahCount)
                val safeDailyAyah = dailyAyahCountInput.coerceIn(1, sameSurah.ayahCount)
                val safeEndAyah = QuranStructure.computeEndAyah(selectedStartSurah, safeStartAyah, safeDailyAyah)
                    ?: safeStartAyah
                val remainingAyahs = sameSurah.ayahCount - safeStartAyah + 1
                val daysToFinish = Math.ceil(remainingAyahs.toDouble() / safeDailyAyah.toDouble()).toInt()
                val ayahForecastText = if (remainingAyahs <= safeDailyAyah) {
                    "اليوم إتمام سورة ${sameSurah.nameArabic} بإذن الله!"
                } else {
                    "إتمام سورة ${sameSurah.nameArabic} خلال $daysToFinish يوم ($remainingAyahs آية متبقية)"
                }

                Text(
                    text = "نطاق الورد: سورة ${sameSurah.nameArabic} • من آية $safeStartAyah إلى آية $safeEndAyah ($safeDailyAyah آية • ص $startPage - $endPage)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = ayahForecastText,
                    fontSize = 11.sp,
                    color = Color(0xFF10B981)
                )
            } else {
                Text(
                    text = "نطاق الورد: من ص $startPage إلى ص $endPage ($calculatedPageCount صفحات)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = forecast.forecastSummary,
                    fontSize = 11.sp,
                    color = Color(0xFF10B981)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSequential) "الورد المتسلسل التلقائي" else "ورد ثابت محدد",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isSequential) "يتقدم تلقائياً كل يوم للصفحات التالية لختم المصحف" else "قراءة نفس السور/الصفحات يومياً دون تقدم",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Switch(
                    checked = isSequential,
                    onCheckedChange = onSequentialChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    Text(
        text = "درجة الصوت: ${volumeLevel.toInt()}%",
        fontSize = 13.sp,
        color = Color(0xFF94A3B8)
    )
    Slider(
        value = volumeLevel,
        onValueChange = onVolumeChange,
        onValueChangeFinished = onVolumeChangeFinished,
        valueRange = 10f..100f,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF10B981),
            activeTrackColor = Color(0xFF10B981),
            inactiveTrackColor = Color(0xFF334155)
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "الاهتزاز المتواصل",
            fontSize = 13.sp,
            color = Color.White
        )
        Switch(
            checked = isVibrate,
            onCheckedChange = onVibrateChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF10B981)
            )
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🔔 نغمة المنبه",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF38BDF8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "النغمة الحالية:",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = selectedSoundTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onTogglePreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayingPreview) Color(0xFFEF4444) else Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isPlayingPreview) "إيقاف ⏹" else "استماع ▶",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "النغمات المتاحة:",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AlarmSoundPresets.PRESET_OPTIONS.forEach { preset ->
                    val isSelected = selectedSoundUri == preset.uriString ||
                        (selectedSoundUri == null && preset.uriString == null) ||
                        (selectedSoundUri == AlarmSoundPresets.URI_DEFAULT && preset.uriString == null)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreset(preset.uriString, preset.title) },
                        label = { Text(preset.title, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFF10B981),
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLaunchDocumentPicker,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text(
                        text = "ملف من الجهاز",
                        fontSize = 11.sp,
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
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text(
                        text = "نغمات الهاتف",
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatAndLabelSection(
    daysList: List<Pair<Int, String>>,
    repeatDays: Set<Int>,
    label: String,
    onRepeatDaysChanged: (Set<Int>) -> Unit,
    onLabelChanged: (String) -> Unit,
    onPresetSelected: (String) -> Unit
) {
    Text(
        text = "التكرار",
        fontSize = 13.sp,
        color = Color(0xFF94A3B8)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = repeatDays.size == 7,
            onClick = {
                onRepeatDaysChanged(
                    setOf(
                        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                    )
                )
            },
            label = { Text("كل يوم", fontSize = 11.sp) }
        )
        FilterChip(
            selected = repeatDays == setOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY
            ),
            onClick = {
                onRepeatDaysChanged(
                    setOf(
                        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                        Calendar.WEDNESDAY, Calendar.THURSDAY
                    )
                )
            },
            label = { Text("أيام العمل", fontSize = 11.sp) }
        )
        FilterChip(
            selected = repeatDays.isEmpty(),
            onClick = { onRepeatDaysChanged(emptySet()) },
            label = { Text("مرة واحدة", fontSize = 11.sp) }
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    val row1Days = remember(daysList) { daysList.take(4) }
    val row2Days = remember(daysList) { daysList.drop(4) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "تسمية المنبه",
        fontSize = 13.sp,
        color = Color(0xFF94A3B8)
    )
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            "صلاة الفجر والورد القرآني",
            "قيام الليل والورد القرآني",
            "سورة الملك قبل النوم",
            "سورة الكهف الجمعة"
        ).forEach { preset ->
            FilterChip(
                selected = label == preset,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset, fontSize = 10.sp) }
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    OutlinedTextField(
        value = label,
        onValueChange = { onLabelChanged(it.take(AlarmItem.MAX_LABEL_LENGTH)) },
        label = { Text("الوصف المخصص") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF10B981),
            unfocusedBorderColor = Color(0xFF64748B)
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayOfWeekRow(
    days: List<Pair<Int, String>>,
    repeatDays: Set<Int>,
    onToggleDay: (Int, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { (dayConst, dayName) ->
            val selected = repeatDays.contains(dayConst)
            FilterChip(
                selected = selected,
                onClick = { onToggleDay(dayConst, selected) },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        text = dayName,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                    selectedLabelColor = Color(0xFF10B981),
                    containerColor = Color(0xFF1E293B),
                    labelColor = Color(0xFF94A3B8)
                )
            )
        }
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
