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
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.ui.AlarmAlertActivity
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
    existingAlarmIds: Set<Int> = emptySet(),
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
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            )
        )
    }
    var isVibrate by remember { mutableStateOf(alarmToEdit?.isVibrationEnabled ?: true) }
    var label by remember { mutableStateOf(alarmToEdit?.label ?: "صلاة الفجر والورد القرآني") }

    // Multi-modal Wird selection (defaults to PAGES)
    var selectionMode by remember {
        mutableStateOf(
            if (alarmToEdit != null) {
                val sSurah = QuranStructure.getSurahAtPage(alarmToEdit.startPage)
                val eSurah = QuranStructure.getLastSurahAtPage(alarmToEdit.endPage)
                if (sSurah.startPage == alarmToEdit.startPage && eSurah.endPage == alarmToEdit.endPage) {
                    WirdSelectionMode.SURAHS
                } else {
                    WirdSelectionMode.PAGES
                }
            } else {
                WirdSelectionMode.PAGES
            }
        )
    }
    var selectedStartSurah by remember { mutableIntStateOf(QuranStructure.getSurahAtPage(alarmToEdit?.startPage ?: 1).number) }
    var selectedEndSurah by remember {
        mutableIntStateOf(
            if (alarmToEdit != null) QuranStructure.getLastSurahAtPage(alarmToEdit.endPage).number
            else QuranStructure.getSurahAtPage(alarmToEdit?.startPage ?: 1).number
        )
    }
    var selectedStartHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.endPage ?: 1)) }
    var selectedStartJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.endPage ?: 1)) }

    var startPage by remember {
        mutableIntStateOf(alarmToEdit?.startPage ?: 1)
    }
    var endPage by remember {
        mutableIntStateOf(alarmToEdit?.endPage ?: 1)
    }

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

    fun playPreview(uriStr: String?, isShort: Boolean = false) {
        stopPreview()
        isPlayingPreview = true
        audioManager.previewSound(
            soundUriString = uriStr,
            volumeLevel = volumeLevel.toInt(),
            durationMs = if (isShort) 2000L else null
        ) {
            isPlayingPreview = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
            audioManager.destroy()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditMode) "تعديل منبه الورد القرآني" else "ضبط منبه الورد القرآني",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )

                OutlinedButton(
                    onClick = {
                        stopPreview()
                        val previewServiceIntent = AlarmAlertIntentFactory.buildStartServiceIntent(
                            context = context,
                            alarmId = -999,
                            label = "معاينة منبه الورد القرآني",
                            targetPage = 1,
                            startPage = 1,
                            pageCount = 1,
                            volumeLevel = volumeLevel.toInt(),
                            isVibrate = isVibrate,
                            soundUri = selectedSoundUri
                        )
                        try {
                            ContextCompat.startForegroundService(context, previewServiceIntent)
                        } catch (_: Exception) {}

                        val previewAlertIntent = AlarmAlertIntentFactory.buildAlertIntent(
                            context = context,
                            alarmId = -999,
                            label = "معاينة منبه الورد القرآني",
                            targetPage = 1,
                            startPage = 1,
                            pageCount = 1,
                            isSnoozeEnabled = true,
                            isFreshTrigger = true
                        ).apply {
                            putExtra(AlarmForegroundService.EXTRA_VOLUME_LEVEL, volumeLevel.toInt())
                            putExtra(AlarmForegroundService.EXTRA_IS_VIBRATE, isVibrate)
                            putExtra(AlarmForegroundService.EXTRA_SOUND_URI, selectedSoundUri)
                        }
                        try {
                            context.startActivity(previewAlertIntent)
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "معاينة",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
                val nextTriggerMillis = previewAlarm.getNextTriggerMillis()
                val targetDayName = if (repeatDays.isNotEmpty() && repeatDays.size < 7) {
                    val targetCal = Calendar.getInstance().apply { timeInMillis = nextTriggerMillis }
                    AlarmUiFormatters.getDayName(targetCal.get(Calendar.DAY_OF_WEEK))
                } else {
                    null
                }
                Text(
                    text = "⏰ " + AlarmUiFormatters.formatNextAlarmCountdown(
                        triggerMillis = nextTriggerMillis,
                        targetDayName = targetDayName
                    ),
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
                        if (isPlayingPreview) {
                            audioManager.updateVolume(newVol.toInt())
                        }
                    },
                    onValueChangeFinished = {
                        if (!isPlayingPreview) {
                            playPreview(selectedSoundUri, isShort = true)
                        }
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

                val row1Days = remember { daysList.take(4) }
                val row2Days = remember { daysList.drop(4) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row1Days.forEach { (dayConst, dayName) ->
                            val selected = repeatDays.contains(dayConst)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    repeatDays = if (selected) repeatDays - dayConst else repeatDays + dayConst
                                },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row2Days.forEach { (dayConst, dayName) ->
                            val selected = repeatDays.contains(dayConst)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    repeatDays = if (selected) repeatDays - dayConst else repeatDays + dayConst
                                },
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

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Preset Label Chips
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
                    val final24Hour = AlarmUiFormatters.toHour24(hour12, isAm)
                    val idToSave = alarmToEdit?.id ?: generateSafeAlarmId(existingAlarmIds)
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

private fun generateSafeAlarmId(existingAlarmIds: Set<Int>): Int {
    val startCandidate = ((System.currentTimeMillis() % 99_999L).toInt()).coerceAtLeast(1)
    var candidate = startCandidate
    repeat(99_999) {
        if (candidate !in existingAlarmIds) return candidate
        candidate = (candidate % 99_999) + 1
    }
    return startCandidate
}
