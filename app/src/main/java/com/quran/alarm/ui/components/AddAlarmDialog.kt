package com.quran.alarm.ui.components

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import com.quran.alarm.ui.theme.QuranColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlarmScreen(
    alarmToEdit: AlarmItem? = null,
    existingAlarmIds: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit,
    onDelete: ((Int) -> Unit)? = null
) {
    val isEditMode = alarmToEdit != null
    val context = LocalContext.current
    val screenKey = alarmToEdit?.id ?: -1

    val initialHour24 = alarmToEdit?.hour ?: 5
    var hour12 by rememberSaveable(screenKey) { mutableIntStateOf(AlarmUiFormatters.getHour12(initialHour24)) }
    var minute by rememberSaveable(screenKey) { mutableIntStateOf(alarmToEdit?.minute ?: 0) }
    var isAm by rememberSaveable(screenKey) { mutableStateOf(AlarmUiFormatters.isAm(initialHour24)) }
    val intSetSaver = remember {
        listSaver<Set<Int>, Int>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    }
    var repeatDays by rememberSaveable(screenKey, stateSaver = intSetSaver) {
        mutableStateOf(
            alarmToEdit?.repeatDays ?: setOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            )
        )
    }
    var isVibrate by rememberSaveable(screenKey) { mutableStateOf(alarmToEdit?.isVibrationEnabled ?: true) }
    var label by rememberSaveable(screenKey) { mutableStateOf(alarmToEdit?.label ?: "صلاة الفجر والورد القرآني") }

    val initialStartPage = (alarmToEdit?.startPage ?: 1).coerceIn(1, QuranStructure.TOTAL_PAGES)
    val initialEndPage = alarmToEdit?.let {
        (it.startPage + it.pageCount - 1).coerceIn(initialStartPage, QuranStructure.TOTAL_PAGES)
    } ?: 1

    val selectionModeSaver = remember {
        listSaver<WirdSelectionMode, String>(
            save = { listOf(it.name) },
            restore = { list ->
                list.firstOrNull()?.let { name -> runCatching { WirdSelectionMode.valueOf(name) }.getOrNull() }
                    ?: WirdSelectionMode.PAGES
            }
        )
    }
    var selectionMode by rememberSaveable(screenKey, stateSaver = selectionModeSaver) {
        mutableStateOf(
            if (alarmToEdit != null) {
                if (alarmToEdit.isAyahMode) {
                    WirdSelectionMode.SURAHS
                } else {
                    val sSurah = QuranStructure.getSurahAtPage(initialStartPage)
                    val eSurah = QuranStructure.getLastSurahAtPage(initialEndPage)
                    if (sSurah.startPage == initialStartPage && eSurah.endPage == initialEndPage) {
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
    var selectedStartSurah by rememberSaveable(screenKey) {
        mutableIntStateOf(
            alarmToEdit?.wirdSurahNumber ?: QuranStructure.getSurahAtPage(initialStartPage).number
        )
    }
    var selectedEndSurah by rememberSaveable(screenKey) {
        mutableIntStateOf(
            when {
                alarmToEdit?.wirdSurahNumber != null -> alarmToEdit.wirdSurahNumber
                alarmToEdit != null -> QuranStructure.getLastSurahAtPage(initialEndPage).number
                else -> QuranStructure.getSurahAtPage(1).number
            }
        )
    }
    var useAyahCount by rememberSaveable(screenKey) { mutableStateOf(alarmToEdit?.isAyahMode ?: false) }
    var startAyahInput by rememberSaveable(screenKey) { mutableIntStateOf(alarmToEdit?.startAyah ?: 1) }
    var dailyAyahCountInput by rememberSaveable(screenKey) { mutableIntStateOf(alarmToEdit?.dailyAyahCount ?: 10) }
    var selectedStartHizb by rememberSaveable(screenKey) {
        mutableIntStateOf(QuranStructure.getHizbAtPage(initialStartPage))
    }
    var selectedEndHizb by rememberSaveable(screenKey) {
        mutableIntStateOf(QuranStructure.getHizbAtPage(initialEndPage))
    }
    var selectedStartJuz by rememberSaveable(screenKey) {
        mutableIntStateOf(QuranStructure.getJuzAtPage(initialStartPage))
    }
    var selectedEndJuz by rememberSaveable(screenKey) {
        mutableIntStateOf(QuranStructure.getJuzAtPage(initialEndPage))
    }

    var startPage by rememberSaveable(screenKey) { mutableIntStateOf(initialStartPage) }
    var endPage by rememberSaveable(screenKey) { mutableIntStateOf(initialEndPage) }

    var showStartSurahPicker by rememberSaveable(screenKey) { mutableStateOf(false) }
    var showEndSurahPicker by rememberSaveable(screenKey) { mutableStateOf(false) }
    var showStartPageInputDialog by rememberSaveable(screenKey) { mutableStateOf(false) }
    var showEndPageInputDialog by rememberSaveable(screenKey) { mutableStateOf(false) }

    // Progressive disclosure collapsed states
    var isSoundSectionExpanded by rememberSaveable(screenKey) { mutableStateOf(false) }
    var isRepeatSectionExpanded by rememberSaveable(screenKey) { mutableStateOf(false) }
    var isLabelSectionExpanded by rememberSaveable(screenKey) { mutableStateOf(false) }

    // Confirmation dialogs
    var showSaveConfirmDialog by rememberSaveable(screenKey) { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable(screenKey) { mutableStateOf(false) }

    fun syncPagesToMode(mode: WirdSelectionMode) {
        selectionMode = mode
        if (mode != WirdSelectionMode.SURAHS) {
            useAyahCount = false
        }
        when (mode) {
            WirdSelectionMode.SURAHS -> {
                if (!(selectedStartSurah == selectedEndSurah && useAyahCount)) {
                    val sPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                    val ePage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                    startPage = sPage
                    endPage = maxOf(sPage, ePage)
                }
            }
            WirdSelectionMode.AHZAB -> {
                val sPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                val ePage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                startPage = sPage
                endPage = maxOf(sPage, ePage)
            }
            WirdSelectionMode.AJZAA -> {
                val sPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                val ePage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                startPage = sPage
                endPage = maxOf(sPage, ePage)
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
                endPage = maxOf(range.startPage, range.endPage)
            }
        }
    }

    var isSequential by rememberSaveable(screenKey) { mutableStateOf(alarmToEdit?.isSequential ?: true) }
    var volumeLevel by rememberSaveable(screenKey) { mutableFloatStateOf(alarmToEdit?.volumeLevel?.toFloat() ?: 100f) }
    var selectedSoundUri by rememberSaveable(screenKey) { mutableStateOf(alarmToEdit?.soundUri) }
    var selectedSoundTitle by rememberSaveable(screenKey) {
        mutableStateOf(
            AlarmUiFormatters.formatCompactSoundTitle(
                alarmToEdit?.soundTitle ?: AlarmSoundPresets.getTitleForUri(alarmToEdit?.soundUri)
            )
        )
    }

    val audioManager = remember { SmartAlarmAudioManager(context) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()
    var pendingCopiedPaths by rememberSaveable(screenKey) { mutableStateOf(emptyList<String>()) }
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
            durationMs = if (isShort) 2000L else null,
            onError = {
                Toast.makeText(context, "تعذر تشغيل النغمة المختارة", Toast.LENGTH_SHORT).show()
            }
        ) {
            isPlayingPreview = false
        }
    }

    BackHandler(enabled = !showSaveConfirmDialog && !showDeleteConfirmDialog) {
        stopPreview()
        onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
            audioManager.destroy()
            val isConfigChange = (context as? Activity)?.isChangingConfigurations == true
            if (!isConfigChange) {
                val keptPath = if (isAlarmSaved) {
                    selectedSoundUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
                } else {
                    null
                }
                pendingCopiedPaths.forEach { path ->
                    if (path != keptPath) {
                        val file = File(path)
                        if (file.exists()) {
                            runCatching { file.delete() }
                        }
                    }
                }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            screenScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    CustomSoundStorage.importAudioFromUri(context, uri)
                }
                if (imported != null) {
                    imported.copiedFile?.let { file ->
                        pendingCopiedPaths = pendingCopiedPaths + file.absolutePath
                    }
                    selectedSoundUri = imported.uriString
                    selectedSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(imported.displayName)
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
                screenScope.launch {
                    val validated = withContext(Dispatchers.IO) {
                        CustomSoundStorage.validateRingtoneUri(context, uri)
                    }
                    if (validated != null) {
                        validated.copiedFile?.let { file ->
                            pendingCopiedPaths = pendingCopiedPaths + file.absolutePath
                        }
                        selectedSoundUri = validated.uriString
                        selectedSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(validated.displayName)
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

    val isCurrentRangeInverted = !QuranStructure.isValidNonInvertedRange(startPage, endPage) ||
        (selectionMode == WirdSelectionMode.SURAHS && selectedStartSurah > selectedEndSurah) ||
        (selectionMode == WirdSelectionMode.AHZAB && selectedStartHizb > selectedEndHizb) ||
        (selectionMode == WirdSelectionMode.AJZAA && selectedStartJuz > selectedEndJuz)

    val calculatedPageCount = if (QuranStructure.isValidNonInvertedRange(startPage, endPage)) {
        QuranStructure.computeNonInvertedPageCount(startPage, endPage)
    } else {
        1
    }
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
            val sPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
            startPage = sPage
            if (endPage < sPage) {
                endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage.coerceAtLeast(sPage)
            }
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
            val ePage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
            endPage = maxOf(startPage, ePage)
        }
    }

    fun persistAndSave() {
        if (isSaving) return
        if (isCurrentRangeInverted) {
            Toast.makeText(
                context,
                "لا يمكن حفظ نطاق معكوس: يجب أن تكون البداية قبل أو مساوية للنهاية",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        isSaving = true
        stopPreview()
        screenScope.launch {
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

                if (!QuranStructure.isValidNonInvertedRange(resolvedStartPage, resolvedEndPage)) {
                    Toast.makeText(
                        context,
                        "لا يمكن حفظ نطاق معكوس: يجب أن تكون البداية قبل أو مساوية للنهاية",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val final24Hour = AlarmUiFormatters.toHour24(hour12, isAm)
                val idToSave = alarmToEdit?.id ?: generateSafeAlarmId(existingAlarmIds)
                val finalPageCount = QuranStructure.computeNonInvertedPageCount(resolvedStartPage, resolvedEndPage)
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
                    soundTitle = AlarmUiFormatters.formatCompactSoundTitle(selectedSoundTitle),
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
    }

    Scaffold(
        containerColor = QuranColors.BgPrimary,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = QuranColors.BgElevated
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        stopPreview()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "رجوع",
                            tint = QuranColors.TextPrimary
                        )
                    }
                },
                title = {
                    Text(
                        text = if (isEditMode) "تعديل منبه الورد القرآني" else "ضبط منبه الورد القرآني",
                        color = QuranColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
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
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, QuranColors.ActivePillBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(34.dp)
                    ) {
                        Text(
                            text = "معاينة",
                            color = QuranColors.ActivePillText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = QuranColors.BgCard,
                border = BorderStroke(1.dp, QuranColors.BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            if (isCurrentRangeInverted) {
                                Toast.makeText(
                                    context,
                                    "لا يمكن حفظ نطاق معكوس: يجب أن تكون البداية قبل أو مساوية للنهاية",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showSaveConfirmDialog = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QuranColors.CtaBg,
                            contentColor = QuranColors.CtaText
                        ),
                        modifier = Modifier
                            .weight(0.68f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (isEditMode) "حفظ التعديلات" else "حفظ المنبه",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            stopPreview()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = QuranColors.TextSecondary
                        ),
                        modifier = Modifier
                            .weight(0.32f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "إلغاء",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "وقت المنبه",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = QuranColors.TextPrimary
            )

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
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = QuranColors.ActivePillText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

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
                onOpenStartPageInput = { showStartPageInputDialog = true },
                onOpenEndPageInput = { showEndPageInputDialog = true },
                onStartHizbSelected = { hizbNum ->
                    selectedStartHizb = hizbNum
                    if (selectedEndHizb < selectedStartHizb) {
                        selectedEndHizb = selectedStartHizb
                        endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                    }
                    startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                    if (endPage < startPage) endPage = startPage
                },
                onEndHizbSelected = { hizbNum ->
                    selectedEndHizb = if (hizbNum < selectedStartHizb) selectedStartHizb else hizbNum
                    endPage = maxOf(startPage, QuranStructure.getEndPageForHizb(selectedEndHizb))
                },
                onStartJuzSelected = { juzNum ->
                    selectedStartJuz = juzNum
                    if (selectedEndJuz < selectedStartJuz) {
                        selectedEndJuz = selectedStartJuz
                        endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                    }
                    startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                    if (endPage < startPage) endPage = startPage
                },
                onEndJuzSelected = { juzNum ->
                    selectedEndJuz = if (juzNum < selectedStartJuz) selectedStartJuz else juzNum
                    endPage = maxOf(startPage, QuranStructure.getEndPageForJuz(selectedEndJuz))
                },
                onStartPageSelected = { p ->
                    startPage = p
                    if (endPage < startPage) endPage = startPage
                },
                onEndPageSelected = { p ->
                    endPage = if (p < startPage) startPage else p
                },
                onQuickPortionSelected = { targetEndPage ->
                    selectionMode = WirdSelectionMode.PAGES
                    useAyahCount = false
                    endPage = targetEndPage.coerceIn(startPage, QuranStructure.TOTAL_PAGES)
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

            // Progressive Disclosure: Sound, Repeat, Label collapsed cleanly by default
            val soundSummary = "${AlarmUiFormatters.formatCompactSoundTitle(selectedSoundTitle)} • ${volumeLevel.toInt()}%${if (isVibrate) " • اهتزاز" else ""}"
            CollapsibleSettingCard(
                title = "🔔 النغمة والصوت",
                summary = soundSummary,
                isExpanded = isSoundSectionExpanded,
                onToggle = { isSoundSectionExpanded = !isSoundSectionExpanded }
            ) {
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
                        selectedSoundTitle = AlarmUiFormatters.formatCompactSoundTitle(title)
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
            }

            CollapsibleSettingCard(
                title = "🔁 التكرار",
                summary = AlarmUiFormatters.formatRepeatDays(repeatDays),
                isExpanded = isRepeatSectionExpanded,
                onToggle = { isRepeatSectionExpanded = !isRepeatSectionExpanded }
            ) {
                RepeatSelectionContent(
                    daysList = daysList,
                    repeatDays = repeatDays,
                    onRepeatDaysChanged = { repeatDays = it }
                )
            }

            CollapsibleSettingCard(
                title = "🏷️ تسمية المنبه",
                summary = label.ifBlank { "ورد القرآن الكريم" },
                isExpanded = isLabelSectionExpanded,
                onToggle = { isLabelSectionExpanded = !isLabelSectionExpanded }
            ) {
                LabelSelectionContent(
                    label = label,
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

            if (isEditMode && onDelete != null && alarmToEdit != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, QuranColors.DangerRed.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = QuranColors.DangerRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حذف المنبه",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showStartSurahPicker) {
        SurahSearchPickerBottomSheet(
            title = "اختر سورة البداية",
            onDismiss = { showStartSurahPicker = false },
            onSelectSurah = { surah -> selectStartSurah(surah.number) }
        )
    }

    if (showEndSurahPicker) {
        SurahSearchPickerBottomSheet(
            title = "اختر سورة الانتهاء",
            onDismiss = { showEndSurahPicker = false },
            onSelectSurah = { surah -> selectEndSurah(surah.number) }
        )
    }

    if (showStartPageInputDialog) {
        PageDirectInputDialog(
            title = "إدخال صفحة البداية",
            initialPage = startPage,
            minAllowedPage = 1,
            onDismiss = { showStartPageInputDialog = false },
            onConfirmPage = { entered ->
                startPage = entered
                if (endPage < entered) endPage = entered
            }
        )
    }

    if (showEndPageInputDialog) {
        PageDirectInputDialog(
            title = "إدخال صفحة الانتهاء",
            initialPage = endPage,
            minAllowedPage = startPage,
            onDismiss = { showEndPageInputDialog = false },
            onConfirmPage = { entered ->
                endPage = entered.coerceAtLeast(startPage)
            }
        )
    }

    if (showSaveConfirmDialog) {
        val isVeryLarge = calculatedPageCount > 20
        val isModeratelyLarge = calculatedPageCount in 11..20
        val rangeText = if (isAyahModeActive) {
            val surah = QuranStructure.getSurahByNumber(selectedStartSurah)
            val safeStart = startAyahInput.coerceIn(1, surah.ayahCount)
            val safeDaily = dailyAyahCountInput.coerceIn(1, surah.ayahCount)
            val safeEnd = QuranStructure.computeEndAyah(selectedStartSurah, safeStart, safeDaily) ?: safeStart
            "سورة ${surah.nameArabic} • من آية $safeStart إلى آية $safeEnd (${AlarmUiFormatters.formatAyahsCount(safeDaily)})"
        } else {
            AlarmUiFormatters.formatPageRangeSummary(startPage, endPage)
        }
        val estimatedTime = if (isAyahModeActive) {
            val surah = QuranStructure.getSurahByNumber(selectedStartSurah)
            AlarmUiFormatters.formatEstimatedReadingTime(
                pageCount = calculatedPageCount,
                ayahCount = dailyAyahCountInput.coerceIn(1, surah.ayahCount),
                surahTotalAyahs = surah.ayahCount
            )
        } else {
            AlarmUiFormatters.formatEstimatedReadingTime(calculatedPageCount)
        }

        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            containerColor = QuranColors.BgCard,
            title = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = "تأكيد حجم الورد قبل الحفظ",
                        color = QuranColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = QuranColors.BgElevated,
                            border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "النطاق المختار: $rangeText",
                                    color = QuranColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 20.sp
                                )
                                Text(
                                    text = "عدد الصفحات الإجمالي: ${AlarmUiFormatters.formatPagesCount(calculatedPageCount)}",
                                    color = QuranColors.ActivePillText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "الوقت التقريبي للقراءة: $estimatedTime",
                                    color = QuranColors.GoldBadgeText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isVeryLarge || isModeratelyLarge) {
                            val warnColor = if (isVeryLarge) QuranColors.DangerRed else QuranColors.WarningOrange
                            val warnBg = if (isVeryLarge) QuranColors.DangerRedBg else QuranColors.WarningOrangeBg
                            val warnMsg = if (isVeryLarge) {
                                "تحذير: حجم الورد كبير جداً (${AlarmUiFormatters.formatPagesCount(calculatedPageCount)})! لن يتم إغلاق شاشة المنبه إلا بعد إتمام قراءة هذا الورد كاملاً."
                            } else {
                                "تنبيه: الورد المختار كبير نسبياً (${AlarmUiFormatters.formatPagesCount(calculatedPageCount)}). يرجى التأكد قبل الحفظ."
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = warnBg,
                                border = BorderStroke(1.dp, warnColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = warnMsg,
                                    color = warnColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSaving,
                    onClick = {
                        showSaveConfirmDialog = false
                        persistAndSave()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVeryLarge) QuranColors.DangerRed else QuranColors.CtaBg,
                        contentColor = if (isVeryLarge) QuranColors.TextPrimary else QuranColors.CtaText
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "موافق وحفظ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveConfirmDialog = false },
                    border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "تعديل",
                        color = QuranColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        )
    }

    if (showDeleteConfirmDialog && alarmToEdit != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = QuranColors.BgCard,
            title = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = "تأكيد حذف المنبه",
                        color = QuranColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = "هل أنت متأكد من حذف منبه (${alarmToEdit.label}) المقرر في ${AlarmUiFormatters.formatTime12Hour(alarmToEdit.hour, alarmToEdit.minute)}؟",
                        color = QuranColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        stopPreview()
                        onDelete(alarmToEdit.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuranColors.DangerRed,
                        contentColor = QuranColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حذف", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء", color = QuranColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
fun AddAlarmDialog(
    alarmToEdit: AlarmItem? = null,
    existingAlarmIds: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit,
    onDelete: ((Int) -> Unit)? = null
) {
    AddEditAlarmScreen(
        alarmToEdit = alarmToEdit,
        existingAlarmIds = existingAlarmIds,
        onDismiss = onDismiss,
        onSave = onSave,
        onDelete = onDelete
    )
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
