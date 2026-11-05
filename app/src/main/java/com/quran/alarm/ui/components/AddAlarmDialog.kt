package com.quran.alarm.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.media.AudioManager
import androidx.compose.material3.OutlinedButton
import android.media.ToneGenerator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.quran.alarm.alarm.audio.SmartAlarmAudioManager
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import com.quran.alarm.alarm.domain.model.SurahInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.model.WirdSelectionMode
import com.quran.alarm.ui.AlarmUiFormatters
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddAlarmDialog(
    alarmToEdit: AlarmItem? = null,
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit
) {
    val isEditMode = alarmToEdit != null
    val context = LocalContext.current
    var showOverlayPermissionPrompt by remember { mutableStateOf(false) }
    var pendingAlarmToSave by remember { mutableStateOf<AlarmItem?>(null) }

    val initialHour24 = alarmToEdit?.hour ?: 5
    var hour12 by remember { mutableIntStateOf(AlarmUiFormatters.getHour12(initialHour24)) }
    var minute by remember { mutableIntStateOf(alarmToEdit?.minute ?: 0) }
    var isAm by remember { mutableStateOf(AlarmUiFormatters.isAm(initialHour24)) }
    var repeatDays by remember {
        mutableStateOf(
            alarmToEdit?.repeatDays ?: setOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY
            )
        )
    }
    var isVibrate by remember { mutableStateOf(alarmToEdit?.isVibrationEnabled ?: true) }
    var label by remember { mutableStateOf(alarmToEdit?.label ?: "صلاة الفجر والورد القرآني") }

    // Multi-modal Wird selection
    var selectionMode by remember { mutableStateOf(WirdSelectionMode.SURAHS) }
    var selectedStartSurah by remember { mutableIntStateOf(QuranStructure.getSurahAtPage(alarmToEdit?.startPage ?: 2).number) }
    var selectedEndSurah by remember { mutableIntStateOf(QuranStructure.getLastSurahAtPage(alarmToEdit?.endPage ?: 6).number) }
    var selectedStartHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.endPage ?: 2)) }
    var selectedStartJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.endPage ?: 1)) }

    var startPage by remember { mutableIntStateOf(alarmToEdit?.startPage ?: 2) }
    var endPage by remember { mutableIntStateOf(alarmToEdit?.endPage ?: 6) }

    var showStartSurahPicker by remember { mutableStateOf(false) }
    var showEndSurahPicker by remember { mutableStateOf(false) }

    fun syncPagesToMode(mode: WirdSelectionMode) {
        selectionMode = mode
        when (mode) {
            WirdSelectionMode.SURAHS -> {
                startPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
            }
            WirdSelectionMode.AHZAB -> {
                startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
            }
            WirdSelectionMode.AJZAA -> {
                startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
            }
            WirdSelectionMode.PAGES -> {
                // retain current pages
            }
        }
    }
    var isSequential by remember { mutableStateOf(alarmToEdit?.isSequential ?: true) }
    var volumeLevel by remember { mutableFloatStateOf(alarmToEdit?.volumeLevel?.toFloat() ?: 100f) }

    var selectedSoundUri by remember { mutableStateOf(alarmToEdit?.soundUri) }
    var selectedSoundTitle by remember {
        mutableStateOf(alarmToEdit?.soundTitle ?: AlarmSoundPresets.getTitleForUri(alarmToEdit?.soundUri))
    }
    val audioManager = remember { SmartAlarmAudioManager(context) }
    var isPlayingPreview by remember { mutableStateOf(false) }

    fun stopPreview() {
        audioManager.stopAlarm()
        isPlayingPreview = false
    }

    fun playPreview(uriStr: String?) {
        stopPreview()
        isPlayingPreview = true
        audioManager.previewSound(uriStr, volumeLevel = volumeLevel.toInt()) {
            isPlayingPreview = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var name: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}

            val finalName = name ?: "ملف صوتي مخصص"
            selectedSoundUri = uri.toString()
            selectedSoundTitle = finalName
            playPreview(uri.toString())
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "نغمة هاتف"
                selectedSoundUri = uri.toString()
                selectedSoundTitle = title
                playPreview(uri.toString())
            }
        }
    }

    val daysList = listOf(
        Calendar.SATURDAY to "السبت",
        Calendar.SUNDAY to "الأحد",
        Calendar.MONDAY to "الاثنين",
        Calendar.TUESDAY to "الثلاثاء",
        Calendar.WEDNESDAY to "الأربعاء",
        Calendar.THURSDAY to "الخميس",
        Calendar.FRIDAY to "الجمعة"
    )

    // Calculate actual page count
    val calculatedPageCount = if (endPage >= startPage) {
        endPage - startPage + 1
    } else {
        (604 - startPage + 1) + endPage
    }

    val forecast = QuranStructure.calculateKhatmaForecast(startPage, calculatedPageCount)

    AlertDialog(
        onDismissRequest = {
            stopPreview()
            onDismiss()
        },
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = if (isEditMode) "تعديل منبه الورد القرآني" else "ضبط منبه الورد القرآني",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Time Selector Header & Modern Islamic Glow Time Card
                Text(
                    text = "وقت المنبه",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(6.dp))

                ModernIslamicTimePickerCard(
                    hour12 = hour12,
                    minute = minute,
                    isAm = isAm,
                    onHourChange = { hour12 = it },
                    onMinuteChange = { minute = it },
                    onAmPmToggle = { isAm = it }
                )

                // Live Alarm Countdown Text
                val current24 = AlarmUiFormatters.toHour24(hour12, isAm)
                val previewAlarm = AlarmItem(id = 0, hour = current24, minute = minute, repeatDays = repeatDays)
                Text(
                    text = "⏰ " + AlarmUiFormatters.formatNextAlarmCountdown(previewAlarm.getNextTriggerMillis()),
                    fontSize = 12.sp,
                    color = Color(0xFF10B981),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Quran Wird Configuration Card
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

                        // Selection Mode Chips (2 rows x 2 items with equal width to prevent text squishing)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(WirdSelectionMode.SURAHS, WirdSelectionMode.AHZAB).forEach { mode ->
                                    FilterChip(
                                        selected = selectionMode == mode,
                                        onClick = { syncPagesToMode(mode) },
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(WirdSelectionMode.AJZAA, WirdSelectionMode.PAGES).forEach { mode ->
                                    FilterChip(
                                        selected = selectionMode == mode,
                                        onClick = { syncPagesToMode(mode) },
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
                                                    .clickable { showStartSurahPicker = true }
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
                                                    .clickable { showEndSurahPicker = true }
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

                                            // Start Surah Wheel
                                            VerticalWheelPicker(
                                                items = allSurahs,
                                                selectedItem = selectedStartSurah,
                                                onItemSelected = { surahNum ->
                                                    selectedStartSurah = surahNum
                                                    if (selectedEndSurah < selectedStartSurah) {
                                                        selectedEndSurah = selectedStartSurah
                                                        endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                                                    }
                                                    startPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                                                },
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

                                            // End Surah Wheel (with auto-validation: end cannot drop below start)
                                            VerticalWheelPicker(
                                                items = allSurahs,
                                                selectedItem = selectedEndSurah,
                                                onItemSelected = { surahNum ->
                                                    val valid = if (surahNum < selectedStartSurah) selectedStartSurah else surahNum
                                                    selectedEndSurah = valid
                                                    endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                                                },
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

                                            // Start Hizb Wheel
                                            VerticalWheelPicker(
                                                items = allAhzab,
                                                selectedItem = selectedStartHizb,
                                                onItemSelected = { hizbNum ->
                                                    selectedStartHizb = hizbNum
                                                    if (selectedEndHizb < selectedStartHizb) {
                                                        selectedEndHizb = selectedStartHizb
                                                        endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                                                    }
                                                    startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                                                },
                                                isInfinite = false,
                                                itemHeight = 44.dp,
                                                fontSize = 13.sp,
                                                showDividers = true,
                                                itemLabel = { "حزب $it (ص ${QuranStructure.getStartPageForHizb(it)})" },
                                                modifier = Modifier.weight(1f)
                                            )

                                            // End Hizb Wheel (with auto-validation: end cannot drop below start)
                                            VerticalWheelPicker(
                                                items = allAhzab,
                                                selectedItem = selectedEndHizb,
                                                onItemSelected = { hizbNum ->
                                                    val valid = if (hizbNum < selectedStartHizb) selectedStartHizb else hizbNum
                                                    selectedEndHizb = valid
                                                    endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                                                },
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

                                            // Start Juz Wheel
                                            VerticalWheelPicker(
                                                items = allAjzaa,
                                                selectedItem = selectedStartJuz,
                                                onItemSelected = { juzNum ->
                                                    selectedStartJuz = juzNum
                                                    if (selectedEndJuz < selectedStartJuz) {
                                                        selectedEndJuz = selectedStartJuz
                                                        endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                                                    }
                                                    startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                                                },
                                                isInfinite = false,
                                                itemHeight = 44.dp,
                                                fontSize = 13.sp,
                                                showDividers = true,
                                                itemLabel = { "جزء $it (ص ${QuranStructure.getStartPageForJuz(it)})" },
                                                modifier = Modifier.weight(1f)
                                            )

                                            // End Juz Wheel (with auto-validation: end cannot drop below start)
                                            VerticalWheelPicker(
                                                items = allAjzaa,
                                                selectedItem = selectedEndJuz,
                                                onItemSelected = { juzNum ->
                                                    val valid = if (juzNum < selectedStartJuz) selectedStartJuz else juzNum
                                                    selectedEndJuz = valid
                                                    endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                                                },
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

                                            // Start Page Wheel
                                            VerticalWheelPicker(
                                                items = allPages,
                                                selectedItem = startPage,
                                                onItemSelected = { p ->
                                                    startPage = p
                                                    if (endPage < startPage) {
                                                        endPage = startPage
                                                    }
                                                },
                                                isInfinite = false,
                                                itemHeight = 44.dp,
                                                fontSize = 16.sp,
                                                showDividers = true,
                                                itemLabel = { "ص $it" },
                                                modifier = Modifier.weight(1f)
                                            )

                                            // End Page Wheel (with auto-validation: end cannot drop below start)
                                            VerticalWheelPicker(
                                                items = allPages,
                                                selectedItem = endPage,
                                                onItemSelected = { p ->
                                                    val valid = if (p < startPage) startPage else p
                                                    endPage = valid
                                                },
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
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Wird Bounds Summary
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sequential vs Fixed Wird Switch
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
                                onCheckedChange = { isSequential = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Volume Level with Live Tone Preview
                Text(
                    text = "درجة الصوت: ${volumeLevel.toInt()}%",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Slider(
                    value = volumeLevel,
                    onValueChange = { newVol ->
                        volumeLevel = newVol
                    },
                    onValueChangeFinished = {
                        try {
                            val tone = ToneGenerator(AudioManager.STREAM_ALARM, volumeLevel.toInt())
                            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                            // Safely release native AudioTrack after sound finishes to prevent AudioTrack leak
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    tone.release()
                                } catch (_: Exception) {}
                            }, 250)
                        } catch (_: Exception) {}
                    },
                    valueRange = 10f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF10B981),
                        activeTrackColor = Color(0xFF10B981),
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )

                // Continuous Vibration
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
                        onCheckedChange = { isVibrate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Alarm Sound Selection Card (Presets & Local File / Ringtone Picker)
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

                        // Currently Selected Sound Display & Live Preview
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
                                    onClick = {
                                        if (isPlayingPreview) {
                                            stopPreview()
                                        } else {
                                            playPreview(selectedSoundUri)
                                        }
                                    },
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
                            text = "النغمات المدمجة المتاحة:",
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
                                    onClick = {
                                        selectedSoundUri = preset.uriString
                                        selectedSoundTitle = preset.title
                                        playPreview(preset.uriString)
                                    },
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

                        // Custom Audio Pickers (From Device Storage / Device Ringtone Picker)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        documentPickerLauncher.launch(arrayOf("audio/*"))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Text("ملف صوتي من الجهاز", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            val currentUri = selectedSoundUri?.let { Uri.parse(it) }
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                                        }
                                        ringtonePickerLauncher.launch(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Text("نغمات الهاتف", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Repeat Schedule
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
                            repeatDays = setOf(
                                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                            )
                        },
                        label = { Text("كل يوم", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = repeatDays == setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY),
                        onClick = {
                            repeatDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
                        },
                        label = { Text("أيام العمل", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = repeatDays.isEmpty(),
                        onClick = { repeatDays = emptySet() },
                        label = { Text("مرة واحدة", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    daysList.forEach { (dayConst, dayName) ->
                        val selected = repeatDays.contains(dayConst)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                repeatDays = if (selected) repeatDays - dayConst else repeatDays + dayConst
                            },
                            label = { Text(dayName, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Preset Label Chips
                Text(
                    text = "تسمية المنبه السريعة",
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
                            onClick = {
                                label = preset
                                if (preset.contains("سورة الملك")) {
                                    selectionMode = WirdSelectionMode.SURAHS
                                    selectedStartSurah = 67
                                    selectedEndSurah = 67
                                    startPage = 562
                                    endPage = 564
                                    isSequential = false
                                } else if (preset.contains("سورة الكهف")) {
                                    selectionMode = WirdSelectionMode.SURAHS
                                    selectedStartSurah = 18
                                    selectedEndSurah = 18
                                    startPage = 293
                                    endPage = 304
                                    isSequential = false
                                }
                            },
                            label = { Text(preset, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    stopPreview()
                    syncPagesToMode(selectionMode)
                    val final24Hour = AlarmUiFormatters.toHour24(hour12, isAm)
                    val idToSave = alarmToEdit?.id ?: (System.currentTimeMillis() % 100000).toInt()
                    val finalPageCount = if (endPage >= startPage) {
                        endPage - startPage + 1
                    } else {
                        (604 - startPage + 1) + endPage
                    }
                    val candidate = AlarmItem(
                        id = idToSave,
                        hour = final24Hour,
                        minute = minute,
                        repeatDays = repeatDays,
                        isEnabled = true,
                        isVibrationEnabled = isVibrate,
                        label = label.ifBlank { "ورد القرآن الكريم" },
                        targetPage = startPage,
                        startPage = startPage,
                        pageCount = finalPageCount,
                        volumeLevel = volumeLevel.toInt(),
                        soundUri = selectedSoundUri,
                        soundTitle = selectedSoundTitle,
                        isSequential = isSequential
                    )
                    val explicitTrigger = if (repeatDays.isEmpty()) candidate.getNextTriggerMillis() else null
                    val savedAlarm = candidate.copy(explicitTriggerMillis = explicitTrigger)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        pendingAlarmToSave = savedAlarm
                        showOverlayPermissionPrompt = true
                    } else {
                        onSave(savedAlarm)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(
                    text = if (isEditMode) "حفظ التعديلات" else "حفظ المنبه",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    stopPreview()
                    onDismiss()
                }
            ) {
                Text("إلغاء", color = Color(0xFF94A3B8))
            }
        }
    )

    if (showOverlayPermissionPrompt && pendingAlarmToSave != null) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionPrompt = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "⚠️ مطلوب تفعيل الظهور فوق التطبيقات",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "لتتمكن شاشة المنبه من تصدر الشاشة بالكامل فورياً وإيقاظك لقراءة الورد القرآني دون الاكتفاء بإشعار علوي، يلزم تفعيل إذن الظهور فوق التطبيقات.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionPrompt = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                        onSave(pendingAlarmToSave!!)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("تفعيل الإذن وحفظ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionPrompt = false
                        onSave(pendingAlarmToSave!!)
                    }
                ) {
                    Text("حفظ بدون الإذن", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (showStartSurahPicker) {
        SurahSearchPickerDialog(
            title = "اختر سورة البداية",
            onDismiss = { showStartSurahPicker = false },
            onSelectSurah = { surah ->
                selectedStartSurah = surah.number
                startPage = surah.startPage
                if (selectedEndSurah < selectedStartSurah) {
                    selectedEndSurah = selectedStartSurah
                    endPage = surah.endPage
                }
            }
        )
    }

    if (showEndSurahPicker) {
        SurahSearchPickerDialog(
            title = "اختر سورة الانتهاء",
            onDismiss = { showEndSurahPicker = false },
            onSelectSurah = { surah ->
                if (surah.number >= selectedStartSurah) {
                    selectedEndSurah = surah.number
                    endPage = surah.endPage
                }
            }
        )
    }
}

@Composable
fun SurahSearchPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelectSurah: (SurahInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSurahs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            QuranStructure.SURAHS
        } else {
            val q = searchQuery.trim()
            QuranStructure.SURAHS.filter {
                it.nameArabic.contains(q) ||
                it.number.toString().contains(q) ||
                it.startPage.toString().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث باسم السورة أو رقمها...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredSurahs, key = { it.number }) { surah ->
                        Surface(
                            onClick = {
                                onSelectSurah(surah)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${surah.number}. سورة ${surah.nameArabic}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "ص ${surah.startPage}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun ModernIslamicTimePickerCard(
    hour12: Int,
    minute: Int,
    isAm: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onAmPmToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val timeBadge = when {
                isAm && hour12 in 3..6 -> "🌅 وقت السحر وصلاة الفجر"
                isAm -> "☀️ صباحاً"
                hour12 >= 9 || hour12 in 1..2 -> "🌙 ثلث الليل الآخر (قيام الليل)"
                else -> "🌆 مساءً"
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = timeBadge,
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // 3 adjacent columns: Hours, Separator, Minutes, AM/PM forced to LTR layout
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Hours wheel (1..12, infinite loop, snap to center)
                    VerticalWheelPicker(
                        items = (1..12).toList(),
                        selectedItem = hour12,
                        onItemSelected = onHourChange,
                        isInfinite = true,
                        itemHeight = 44.dp,
                        fontSize = 28.sp,
                        showDividers = true,
                        itemLabel = { String.format("%02d", it) },
                        modifier = Modifier.weight(1f)
                    )

                    // Fixed Colon separator
                    Box(
                        modifier = Modifier
                            .height(44.dp * 3),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ":",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Minutes wheel (0..59, infinite loop, snap to center)
                    VerticalWheelPicker(
                        items = (0..59).toList(),
                        selectedItem = minute,
                        onItemSelected = onMinuteChange,
                        isInfinite = true,
                        itemHeight = 44.dp,
                        fontSize = 28.sp,
                        showDividers = true,
                        itemLabel = { String.format("%02d", it) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Period wheel (ص / م)
                    VerticalWheelPicker(
                        items = listOf(true, false),
                        selectedItem = isAm,
                        onItemSelected = onAmPmToggle,
                        isInfinite = false,
                        itemHeight = 44.dp,
                        fontSize = 22.sp,
                        showDividers = true,
                        itemLabel = { if (it) "ص" else "م" },
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Reusable vertical scroll wheel / drum picker matching Alarmy design:
 * - 3 visible items vertically (center is selected in large bold white; top and bottom in dim gray)
 * - 2 thin horizontal divider lines separating selected middle item
 * - Snap to center
 * - Infinite loop support for hours/minutes
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> VerticalWheelPicker(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isInfinite: Boolean = false,
    itemHeight: Dp = 44.dp,
    visibleItemsCount: Int = 3,
    showDividers: Boolean = true,
    fontSize: TextUnit = 22.sp,
    itemLabel: (T) -> String = { it.toString() }
) {
    if (items.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    val isEffectivelyInfinite = isInfinite && items.size > 1
    val repeatCount = 10_000
    val middleBase = if (isEffectivelyInfinite) (repeatCount / 2) * items.size else 0

    val initialIndex = remember(items, selectedItem) {
        val idx = items.indexOf(selectedItem)
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (isEffectivelyInfinite) {
            middleBase + initialIndex - 1
        } else {
            initialIndex
        }
    )

    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Sync external programmatic updates to selectedItem or bounce back if parent clamped invalid selection
    LaunchedEffect(selectedItem, listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val targetIdx = items.indexOf(selectedItem)
            if (targetIdx >= 0) {
                if (isEffectivelyInfinite) {
                    val currentCenter = (listState.firstVisibleItemIndex + 1) % items.size
                    if (currentCenter != targetIdx) {
                        val currentCenterAbsolute = listState.firstVisibleItemIndex + 1
                        val cycleBase = (currentCenterAbsolute / items.size) * items.size
                        val targetFirst = (cycleBase + targetIdx - 1).let {
                            if (it < 0) it + items.size else it
                        }
                        listState.animateScrollToItem(targetFirst)
                    }
                } else {
                    if (listState.firstVisibleItemIndex != targetIdx) {
                        listState.animateScrollToItem(targetIdx)
                    }
                }
            }
        }
    }

    // Emit selection when scrolling stops
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .collect { (firstIndex, isScrolling) ->
                if (!isScrolling) {
                    val selected = if (isEffectivelyInfinite) {
                        val centerIndex = (firstIndex + 1) % items.size
                        items.getOrNull(centerIndex)
                    } else {
                        items.getOrNull(firstIndex)
                    }
                    if (selected != null && selected != selectedItem) {
                        onItemSelected(selected)
                    }
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
    ) {
        if (isEffectivelyInfinite) {
            val totalCount = items.size * repeatCount
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(totalCount) { index ->
                    val actualItem = items[index % items.size]
                    val isCenter = index == listState.firstVisibleItemIndex + 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable {
                                coroutineScope.launch {
                                    val targetScroll = (index - 1).coerceAtLeast(0)
                                    listState.animateScrollToItem(targetScroll)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = itemLabel(actualItem),
                            fontSize = if (isCenter) fontSize else (fontSize.value * 0.78f).sp,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCenter) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.alpha(if (isCenter) 1f else 0.35f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            val paddedItems = remember(items) { listOf(null) + items + listOf(null) }
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(paddedItems.size) { index ->
                    val item = paddedItems[index]
                    val isCenter = index == listState.firstVisibleItemIndex + 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .then(
                                if (item != null) {
                                    Modifier.clickable {
                                        coroutineScope.launch {
                                            val targetFirst = (index - 1).coerceIn(0, items.size - 1)
                                            listState.animateScrollToItem(targetFirst)
                                        }
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item != null) {
                            Text(
                                text = itemLabel(item),
                                fontSize = if (isCenter) fontSize else (fontSize.value * 0.78f).sp,
                                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCenter) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.alpha(if (isCenter) 1f else 0.35f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (showDividers) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = itemHeight)
                    .height(1.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.4f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = itemHeight * 2)
                    .height(1.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.4f))
            )
        }
    }
}
