package com.quran.alarm.ui.components

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.quran.alarm.alarm.audio.SmartAlarmAudioManager
import com.quran.alarm.alarm.data.storage.CustomSoundStorage
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.AlarmSoundPresets
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.model.WirdSelectionMode
import com.quran.alarm.alarm.service.AlarmAlertIntentFactory
import com.quran.alarm.alarm.service.AlarmForegroundService
import com.quran.alarm.quran.data.AyahInfoDatabase
import com.quran.alarm.ui.AlarmUiFormatters
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddAlarmDialog(
    alarmToEdit: AlarmItem? = null,
    existingAlarmIds: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit
) {
    val isEditMode = alarmToEdit != null
    val context = LocalContext.current

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

    var selectionMode by remember {
        mutableStateOf(
            if (alarmToEdit != null) {
                if (alarmToEdit.isAyahMode) {
                    WirdSelectionMode.SURAHS
                } else {
                    val sSurah = QuranStructure.getSurahAtPage(alarmToEdit.startPage)
                    val eSurah = QuranStructure.getLastSurahAtPage(alarmToEdit.endPage)
                    if (sSurah.startPage == alarmToEdit.startPage && eSurah.endPage == alarmToEdit.endPage) {
                        WirdSelectionMode.SURAHS
                    } else {
                        WirdSelectionMode.PAGES
                    }
                }
            } else {
                WirdSelectionMode.PAGES
            }
        )
    }
    var selectedStartSurah by remember {
        mutableIntStateOf(
            alarmToEdit?.wirdSurahNumber ?: QuranStructure.getSurahAtPage(alarmToEdit?.startPage ?: 1).number
        )
    }
    var selectedEndSurah by remember {
        mutableIntStateOf(
            when {
                alarmToEdit?.wirdSurahNumber != null -> alarmToEdit.wirdSurahNumber
                alarmToEdit != null -> QuranStructure.getLastSurahAtPage(alarmToEdit.endPage).number
                else -> QuranStructure.getSurahAtPage(1).number
            }
        )
    }
    var useAyahCount by remember { mutableStateOf(alarmToEdit?.isAyahMode ?: false) }
    var startAyahInput by remember { mutableIntStateOf(alarmToEdit?.startAyah ?: 1) }
    var dailyAyahCountInput by remember { mutableIntStateOf(alarmToEdit?.dailyAyahCount ?: 10) }
    var selectedStartHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.endPage ?: 1)) }
    var selectedStartJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.endPage ?: 1)) }

    var startPage by remember { mutableIntStateOf(alarmToEdit?.startPage ?: 1) }
    var endPage by remember { mutableIntStateOf(alarmToEdit?.endPage ?: 1) }

    var showStartSurahPicker by remember { mutableStateOf(false) }
    var showEndSurahPicker by remember { mutableStateOf(false) }

    fun syncPagesToMode(mode: WirdSelectionMode) {
        selectionMode = mode
        if (mode != WirdSelectionMode.SURAHS) {
            useAyahCount = false
        }
        when (mode) {
            WirdSelectionMode.SURAHS -> {
                if (!(selectedStartSurah == selectedEndSurah && useAyahCount)) {
                    startPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                    endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                }
            }
            WirdSelectionMode.AHZAB -> {
                startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
            }
            WirdSelectionMode.AJZAA -> {
                startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
            }
            WirdSelectionMode.PAGES -> Unit
        }
    }

    val isAyahModeActive = selectionMode == WirdSelectionMode.SURAHS &&
        selectedStartSurah == selectedEndSurah &&
        useAyahCount

    LaunchedEffect(
        selectionMode,
        selectedStartSurah,
        selectedEndSurah,
        useAyahCount,
        startAyahInput,
        dailyAyahCountInput
    ) {
        if (isAyahModeActive) {
            val surah = QuranStructure.getSurahByNumber(selectedStartSurah)
            val safeStart = startAyahInput.coerceIn(1, surah.ayahCount)
            val safeEnd = QuranStructure.computeEndAyah(selectedStartSurah, safeStart, dailyAyahCountInput) ?: safeStart
            val range = withContext(Dispatchers.IO) {
                runCatching {
                    AyahInfoDatabase.getInstanceAsync(context).getAyahRangeInfo(selectedStartSurah, safeStart, safeEnd)
                }.getOrNull()
            }
            if (range != null) {
                startPage = range.startPage
                endPage = range.endPage
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
    val dialogScope = rememberCoroutineScope()
    val pendingCopiedSoundFiles = remember { mutableListOf<File>() }
    var isAlarmSaved by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

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
            val keptPath = if (isAlarmSaved) {
                selectedSoundUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
            } else {
                null
            }
            pendingCopiedSoundFiles.forEach { file ->
                if (file.absolutePath != keptPath && file.exists()) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            dialogScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    CustomSoundStorage.importAudioFromUri(context, uri)
                }
                if (imported != null) {
                    imported.copiedFile?.let { pendingCopiedSoundFiles.add(it) }
                    selectedSoundUri = imported.uriString
                    selectedSoundTitle = imported.displayName
                    playPreview(imported.uriString)
                } else {
                    Toast.makeText(
                        context,
                        "تعذر اختيار الملف: تأكد أنه ملف صوتي صالح بحجم أقل من 15 ميجابايت",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
                dialogScope.launch {
                    val validated = withContext(Dispatchers.IO) {
                        CustomSoundStorage.validateRingtoneUri(context, uri)
                    }
                    if (validated != null) {
                        validated.copiedFile?.let { pendingCopiedSoundFiles.add(it) }
                        selectedSoundUri = validated.uriString
                        selectedSoundTitle = validated.displayName
                        playPreview(validated.uriString)
                    } else {
                        Toast.makeText(
                            context,
                            "تعذر التحقق من النغمة المختارة",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    val daysList = remember {
        listOf(
            Calendar.SATURDAY to "السبت",
            Calendar.SUNDAY to "الأحد",
            Calendar.MONDAY to "الاثنين",
            Calendar.TUESDAY to "الثلاثاء",
            Calendar.WEDNESDAY to "الأربعاء",
            Calendar.THURSDAY to "الخميس",
            Calendar.FRIDAY to "الجمعة"
        )
    }

    val calculatedPageCount = QuranStructure.computePageCount(startPage, endPage)
    val forecast = QuranStructure.calculateKhatmaForecast(startPage, calculatedPageCount)

    fun selectStartSurah(surahNum: Int) {
        selectedStartSurah = surahNum
        if (selectedEndSurah < selectedStartSurah) {
            selectedEndSurah = selectedStartSurah
            endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
        }
        if (selectedStartSurah != selectedEndSurah) {
            useAyahCount = false
        } else {
            val maxAyahs = QuranStructure.getSurahByNumber(surahNum).ayahCount
            startAyahInput = startAyahInput.coerceIn(1, maxAyahs)
            dailyAyahCountInput = dailyAyahCountInput.coerceIn(1, maxAyahs)
        }
        if (!useAyahCount) {
            startPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
        }
    }

    fun selectEndSurah(surahNum: Int) {
        val valid = if (surahNum < selectedStartSurah) selectedStartSurah else surahNum
        selectedEndSurah = valid
        if (selectedStartSurah != selectedEndSurah) {
            useAyahCount = false
        } else {
            val maxAyahs = QuranStructure.getSurahByNumber(valid).ayahCount
            startAyahInput = startAyahInput.coerceIn(1, maxAyahs)
            dailyAyahCountInput = dailyAyahCountInput.coerceIn(1, maxAyahs)
        }
        if (!useAyahCount) {
            endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
        }
    }

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
                        val previewAlertIntent = AlarmAlertIntentFactory.buildAlertIntent(
                            context = context,
                            alarmId = AlarmAlertIntentFactory.PREVIEW_ALARM_ID,
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
                        val activityLaunched = runCatching {
                            context.startActivity(previewAlertIntent)
                        }.isSuccess

                        if (activityLaunched) {
                            val previewServiceIntent = AlarmAlertIntentFactory.buildStartServiceIntent(
                                context = context,
                                alarmId = AlarmAlertIntentFactory.PREVIEW_ALARM_ID,
                                label = "معاينة منبه الورد القرآني",
                                targetPage = 1,
                                startPage = 1,
                                pageCount = 1,
                                volumeLevel = volumeLevel.toInt(),
                                isVibrate = isVibrate,
                                soundUri = selectedSoundUri
                            )
                            runCatching {
                                ContextCompat.startForegroundService(context, previewServiceIntent)
                            }
                        }
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

                val (nextTriggerMillis, targetDayName) = remember(hour12, minute, isAm, repeatDays) {
                    val current24 = AlarmUiFormatters.toHour24(hour12, isAm)
                    val previewAlarm = AlarmItem(id = 0, hour = current24, minute = minute, repeatDays = repeatDays)
                    val trigger = previewAlarm.getNextTriggerMillis()
                    val dayName = if (repeatDays.isNotEmpty() && repeatDays.size < 7) {
                        val targetCal = Calendar.getInstance().apply { timeInMillis = trigger }
                        AlarmUiFormatters.getDayName(targetCal.get(Calendar.DAY_OF_WEEK))
                    } else {
                        null
                    }
                    trigger to dayName
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

                WirdConfigurationSection(
                    selectionMode = selectionMode,
                    selectedStartSurah = selectedStartSurah,
                    selectedEndSurah = selectedEndSurah,
                    selectedStartHizb = selectedStartHizb,
                    selectedEndHizb = selectedEndHizb,
                    selectedStartJuz = selectedStartJuz,
                    selectedEndJuz = selectedEndJuz,
                    startPage = startPage,
                    endPage = endPage,
                    useAyahCount = useAyahCount,
                    startAyahInput = startAyahInput,
                    dailyAyahCountInput = dailyAyahCountInput,
                    isAyahModeActive = isAyahModeActive,
                    isSequential = isSequential,
                    calculatedPageCount = calculatedPageCount,
                    forecast = forecast,
                    onSelectMode = ::syncPagesToMode,
                    onStartSurahSelected = ::selectStartSurah,
                    onEndSurahSelected = ::selectEndSurah,
                    onOpenStartSurahPicker = { showStartSurahPicker = true },
                    onOpenEndSurahPicker = { showEndSurahPicker = true },
                    onStartHizbSelected = { hizbNum ->
                        selectedStartHizb = hizbNum
                        if (selectedEndHizb < selectedStartHizb) {
                            selectedEndHizb = selectedStartHizb
                            endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                        }
                        startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                    },
                    onEndHizbSelected = { hizbNum ->
                        selectedEndHizb = if (hizbNum < selectedStartHizb) selectedStartHizb else hizbNum
                        endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                    },
                    onStartJuzSelected = { juzNum ->
                        selectedStartJuz = juzNum
                        if (selectedEndJuz < selectedStartJuz) {
                            selectedEndJuz = selectedStartJuz
                            endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                        }
                        startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                    },
                    onEndJuzSelected = { juzNum ->
                        selectedEndJuz = if (juzNum < selectedStartJuz) selectedStartJuz else juzNum
                        endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                    },
                    onStartPageSelected = { p ->
                        startPage = p
                        if (endPage < startPage) endPage = startPage
                    },
                    onEndPageSelected = { p ->
                        endPage = if (p < startPage) startPage else p
                    },
                    onToggleAyahCount = { enabled ->
                        val sameSurah = QuranStructure.getSurahByNumber(selectedStartSurah)
                        useAyahCount = enabled
                        if (enabled) {
                            startAyahInput = startAyahInput.coerceIn(1, sameSurah.ayahCount)
                            dailyAyahCountInput = dailyAyahCountInput.coerceIn(1, sameSurah.ayahCount)
                        } else {
                            startPage = sameSurah.startPage
                            endPage = sameSurah.endPage
                        }
                    },
                    onStartAyahChanged = { startAyahInput = it },
                    onDailyAyahCountChanged = { dailyAyahCountInput = it },
                    onSequentialChanged = { isSequential = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                AlarmAudioAndSoundSection(
                    volumeLevel = volumeLevel,
                    isVibrate = isVibrate,
                    selectedSoundUri = selectedSoundUri,
                    selectedSoundTitle = selectedSoundTitle,
                    isPlayingPreview = isPlayingPreview,
                    onVolumeChange = { newVol ->
                        volumeLevel = newVol
                        if (isPlayingPreview) {
                            audioManager.updateVolume(newVol.toInt())
                        }
                    },
                    onVolumeChangeFinished = {
                        if (!isPlayingPreview) {
                            playPreview(selectedSoundUri, isShort = true)
                        }
                    },
                    onVibrateChange = { isVibrate = it },
                    onTogglePreview = {
                        if (isPlayingPreview) stopPreview() else playPreview(selectedSoundUri)
                    },
                    onSelectPreset = { uriStr, title ->
                        selectedSoundUri = uriStr
                        selectedSoundTitle = title
                        playPreview(uriStr)
                    },
                    onLaunchDocumentPicker = {
                        runCatching { documentPickerLauncher.launch(arrayOf("audio/*")) }
                    },
                    onLaunchRingtonePicker = {
                        runCatching {
                            ringtonePickerLauncher.launch(buildRingtonePickerIntent(selectedSoundUri))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                RepeatAndLabelSection(
                    daysList = daysList,
                    repeatDays = repeatDays,
                    label = label,
                    onRepeatDaysChanged = { repeatDays = it },
                    onLabelChanged = { label = it },
                    onPresetSelected = { preset ->
                        label = preset
                        if (preset.contains("سورة الملك")) {
                            selectionMode = WirdSelectionMode.SURAHS
                            selectedStartSurah = 67
                            selectedEndSurah = 67
                            useAyahCount = false
                            startPage = 562
                            endPage = 564
                            isSequential = false
                        } else if (preset.contains("سورة الكهف")) {
                            selectionMode = WirdSelectionMode.SURAHS
                            selectedStartSurah = 18
                            selectedEndSurah = 18
                            useAyahCount = false
                            startPage = 293
                            endPage = 304
                            isSequential = false
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    stopPreview()
                    dialogScope.launch {
                        try {
                            var resolvedStartPage = startPage
                            var resolvedEndPage = endPage
                            val savedSurahNumber = if (isAyahModeActive) selectedStartSurah else null
                            val savedMaxAyahs = savedSurahNumber?.let { QuranStructure.getSurahByNumber(it).ayahCount }
                            val savedStartAyah = if (savedMaxAyahs != null) startAyahInput.coerceIn(1, savedMaxAyahs) else null
                            val savedDailyAyahCount = if (savedMaxAyahs != null) dailyAyahCountInput.coerceIn(1, savedMaxAyahs) else null

                            if (savedSurahNumber != null && savedStartAyah != null && savedDailyAyahCount != null) {
                                val savedEndAyah = QuranStructure.computeEndAyah(
                                    savedSurahNumber,
                                    savedStartAyah,
                                    savedDailyAyahCount
                                ) ?: savedStartAyah
                                val range = withContext(Dispatchers.IO) {
                                    runCatching {
                                        AyahInfoDatabase.getInstanceAsync(context)
                                            .getAyahRangeInfo(savedSurahNumber, savedStartAyah, savedEndAyah)
                                    }.getOrNull()
                                }
                                if (range != null) {
                                    resolvedStartPage = range.startPage
                                    resolvedEndPage = range.endPage
                                }
                            }

                            val final24Hour = AlarmUiFormatters.toHour24(hour12, isAm)
                            val idToSave = alarmToEdit?.id ?: generateSafeAlarmId(existingAlarmIds)
                            val finalPageCount = QuranStructure.computePageCount(resolvedStartPage, resolvedEndPage)
                            val candidate = AlarmItem(
                                id = idToSave,
                                hour = final24Hour,
                                minute = minute,
                                repeatDays = repeatDays,
                                isEnabled = true,
                                isVibrationEnabled = isVibrate,
                                label = label.ifBlank { "ورد القرآن الكريم" },
                                targetPage = resolvedStartPage,
                                startPage = resolvedStartPage,
                                pageCount = finalPageCount,
                                volumeLevel = volumeLevel.toInt(),
                                soundUri = selectedSoundUri,
                                soundTitle = selectedSoundTitle,
                                isSequential = isSequential,
                                wirdSurahNumber = savedSurahNumber,
                                startAyah = savedStartAyah,
                                dailyAyahCount = savedDailyAyahCount
                            )
                            val explicitTrigger = if (repeatDays.isEmpty()) candidate.getNextTriggerMillis() else null
                            val savedAlarm = candidate.copy(explicitTriggerMillis = explicitTrigger)
                            isAlarmSaved = true
                            onSave(savedAlarm)
                        } finally {
                            isSaving = false
                        }
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

    if (showStartSurahPicker) {
        SurahSearchPickerDialog(
            title = "اختر سورة البداية",
            onDismiss = { showStartSurahPicker = false },
            onSelectSurah = { surah -> selectStartSurah(surah.number) }
        )
    }

    if (showEndSurahPicker) {
        SurahSearchPickerDialog(
            title = "اختر سورة الانتهاء",
            onDismiss = { showEndSurahPicker = false },
            onSelectSurah = { surah -> selectEndSurah(surah.number) }
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
