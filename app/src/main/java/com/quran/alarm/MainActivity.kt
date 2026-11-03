package com.quran.alarm

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.alarm.domain.model.WirdSelectionMode
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.ui.AlarmUiFormatters
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var scheduler: AlarmScheduler
    private lateinit var repository: AlarmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduler = AlarmSchedulerImpl(this)
        repository = AlarmRepositoryImpl(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    AlarmMainScreen(
                        repository = repository,
                        scheduler = scheduler,
                        onShowToast = { msg ->
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmMainScreen(
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
    onShowToast: (String) -> Unit
) {
    var alarmList by remember { mutableStateOf(repository.getAllAlarms()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var alarmToDelete by remember { mutableStateOf<AlarmItem?>(null) }

    fun refreshAlarms() {
        alarmList = repository.getAllAlarms()
    }

    val activeAlarms = alarmList.filter { it.isEnabled }
    val nextAlarmMillis = activeAlarms.map { it.getNextTriggerMillis() }.minOrNull()
    val primaryAlarm = activeAlarms.firstOrNull() ?: alarmList.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "منبه القرآن الكريم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (nextAlarmMillis != null) {
                                AlarmUiFormatters.formatNextAlarmCountdown(nextAlarmMillis)
                            } else {
                                "لا توجد منبهات نشطة"
                            },
                            fontSize = 12.sp,
                            color = if (nextAlarmMillis != null) Color(0xFF10B981) else Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF10B981),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة منبه"
                )
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // 1. Khatma Progress Bar
            if (primaryAlarm != null) {
                val forecast = QuranStructure.calculateKhatmaForecast(
                    startPage = primaryAlarm.startPage,
                    dailyPages = primaryAlarm.pageCount
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📖 مسيرة ختمة القرآن الكريم",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = "${forecast.progressPercentage.toInt()}% (صفحة ${primaryAlarm.startPage} من 604)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = (forecast.progressPercentage / 100f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF334155),
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = forecast.forecastSummary,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. Alarms List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قائمة المنبهات (${alarmList.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "انقر على أي منبه لتعديله",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (alarmList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد منبهات. اضغط على (+) لإضافة منبه جديد.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alarmList) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onClick = { editingAlarm = alarm },
                            onToggle = { isEnabled ->
                                val updated = alarm.copy(isEnabled = isEnabled)
                                repository.saveAlarm(updated)
                                if (isEnabled) {
                                    scheduler.schedule(updated)
                                } else {
                                    scheduler.cancel(updated)
                                }
                                refreshAlarms()
                                onShowToast(if (isEnabled) "تم تفعيل المنبه" else "تم تعطيل المنبه")
                            },
                            onDelete = {
                                alarmToDelete = alarm
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || editingAlarm != null) {
        AddAlarmDialog(
            alarmToEdit = editingAlarm,
            onDismiss = {
                showAddDialog = false
                editingAlarm = null
            },
            onSave = { savedAlarm ->
                repository.saveAlarm(savedAlarm)
                if (savedAlarm.isEnabled) {
                    scheduler.schedule(savedAlarm)
                } else {
                    scheduler.cancel(savedAlarm)
                }
                refreshAlarms()
                val isEdit = editingAlarm != null
                showAddDialog = false
                editingAlarm = null
                onShowToast(if (isEdit) "تم تحديث المنبه بنجاح!" else "تم حفظ المنبه بنجاح!")
            }
        )
    }

    // Delete Confirmation Dialog
    if (alarmToDelete != null) {
        val target = alarmToDelete!!
        AlertDialog(
            onDismissRequest = { alarmToDelete = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "تأكيد حذف المنبه",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف منبه (${target.label}) المقرر في ${AlarmUiFormatters.formatTime12Hour(target.hour, target.minute)}؟",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scheduler.cancel(target)
                        repository.deleteAlarm(target.id)
                        refreshAlarms()
                        alarmToDelete = null
                        onShowToast("تم حذف المنبه")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { alarmToDelete = null }) {
                    Text("إلغاء", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmItem,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AlarmUiFormatters.formatTime12Hour(alarm.hour, alarm.minute),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) Color.White else Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (alarm.isSequential) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (alarm.isSequential) "ورد متسلسل" else "ورد ثابت",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alarm.isSequential) Color(0xFF10B981) else Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${AlarmUiFormatters.formatRepeatDays(alarm.repeatDays)} • ${alarm.label}",
                    fontSize = 13.sp,
                    color = if (alarm.isEnabled) Color(0xFF94A3B8) else Color(0xFF475569)
                )

                Spacer(modifier = Modifier.height(3.dp))

                val surahAtStart = QuranStructure.getSurahAtPage(alarm.startPage)
                Text(
                    text = "سورة ${surahAtStart.nameArabic} • من صفحة ${alarm.startPage} إلى ${alarm.endPage} (${alarm.pageCount} صفحات) • صوت ${alarm.volumeLevel}%${if (alarm.isVibrationEnabled) " • اهتزاز" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.isEnabled) Color(0xFF38BDF8) else Color(0xFF475569)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل المنبه",
                        tint = Color(0xFF94A3B8)
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف المنبه",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddAlarmDialog(
    alarmToEdit: AlarmItem? = null,
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit
) {
    val isEditMode = alarmToEdit != null

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
    var selectedEndSurah by remember { mutableIntStateOf(QuranStructure.getSurahAtPage(alarmToEdit?.endPage ?: 6).number) }
    var selectedStartHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndHizb by remember { mutableIntStateOf(QuranStructure.getHizbAtPage(alarmToEdit?.endPage ?: 2)) }
    var selectedStartJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.startPage ?: 1)) }
    var selectedEndJuz by remember { mutableIntStateOf(QuranStructure.getJuzAtPage(alarmToEdit?.endPage ?: 1)) }

    var startPage by remember { mutableIntStateOf(alarmToEdit?.startPage ?: 2) }
    var endPage by remember { mutableIntStateOf(alarmToEdit?.endPage ?: 6) }
    var isSequential by remember { mutableStateOf(alarmToEdit?.isSequential ?: true) }
    var volumeLevel by remember { mutableFloatStateOf(alarmToEdit?.volumeLevel?.toFloat() ?: 100f) }

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
        onDismissRequest = onDismiss,
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
                // 1. Time Selector Header
                Text(
                    text = "وقت المنبه",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Explicit AM / PM Segmented Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { isAm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAm) Color(0xFF10B981) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "صباحاً (AM)",
                            fontWeight = if (isAm) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAm) Color.White else Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { isAm = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isAm) Color(0xFF38BDF8) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "مساءً (PM)",
                            fontWeight = if (!isAm) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isAm) Color.White else Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hour & Exact Minute Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { hour12 = if (hour12 == 1) 12 else hour12 - 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", fontSize = 18.sp)
                        }
                        Text(
                            text = String.format("%02d", hour12),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Button(
                            onClick = { hour12 = if (hour12 == 12) 1 else hour12 + 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 18.sp)
                        }
                    }

                    Text(
                        text = ":",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Exact Minute selector (00..59)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { minute = if (minute == 0) 59 else minute - 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", fontSize = 18.sp)
                        }
                        Text(
                            text = String.format("%02d", minute),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Button(
                            onClick = { minute = (minute + 1) % 60 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 18.sp)
                        }
                    }
                }

                // Smooth Minute Slider (Direct drag to any minute 00..59)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دقيقة: $minute",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.width(55.dp)
                    )
                    Slider(
                        value = minute.toFloat(),
                        onValueChange = { minute = it.toInt().coerceIn(0, 59) },
                        valueRange = 0f..59f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                }

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
                                        onClick = { selectionMode = mode },
                                        modifier = Modifier.weight(1f),
                                        label = {
                                            Text(
                                                text = mode.titleArabic,
                                                fontSize = 13.sp,
                                                fontWeight = if (selectionMode == mode) FontWeight.Bold else FontWeight.Medium,
                                                textAlign = TextAlign.Center,
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
                                        onClick = { selectionMode = mode },
                                        modifier = Modifier.weight(1f),
                                        label = {
                                            Text(
                                                text = mode.titleArabic,
                                                fontSize = 13.sp,
                                                fontWeight = if (selectionMode == mode) FontWeight.Bold else FontWeight.Medium,
                                                textAlign = TextAlign.Center,
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

                        when (selectionMode) {
                            WirdSelectionMode.SURAHS -> {
                                val startSurahObj = QuranStructure.getSurahByNumber(selectedStartSurah)
                                val endSurahObj = QuranStructure.getSurahByNumber(selectedEndSurah)

                                // Start Surah
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "البدء من: سورة ${startSurahObj.nameArabic} (ص ${startSurahObj.startPage})",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Row {
                                        Button(
                                            onClick = {
                                                if (selectedStartSurah > 1) {
                                                    selectedStartSurah--
                                                    startPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                if (selectedStartSurah < 114) {
                                                    selectedStartSurah++
                                                    startPage = QuranStructure.getSurahByNumber(selectedStartSurah).startPage
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // End Surah
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الانتهاء في: سورة ${endSurahObj.nameArabic} (ص ${endSurahObj.endPage})",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Row {
                                        Button(
                                            onClick = {
                                                if (selectedEndSurah > selectedStartSurah) {
                                                    selectedEndSurah--
                                                    endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                if (selectedEndSurah < 114) {
                                                    selectedEndSurah++
                                                    endPage = QuranStructure.getSurahByNumber(selectedEndSurah).endPage
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }
                            }

                            WirdSelectionMode.AHZAB -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "البدء من: الحزب $selectedStartHizb (ص ${QuranStructure.getStartPageForHizb(selectedStartHizb)})",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Row {
                                        Button(
                                            onClick = {
                                                if (selectedStartHizb > 1) {
                                                    selectedStartHizb--
                                                    startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                if (selectedStartHizb < 60) {
                                                    selectedStartHizb++
                                                    startPage = QuranStructure.getStartPageForHizb(selectedStartHizb)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الانتهاء في: الحزب $selectedEndHizb (ص ${QuranStructure.getEndPageForHizb(selectedEndHizb)})",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Row {
                                        Button(
                                            onClick = {
                                                if (selectedEndHizb > selectedStartHizb) {
                                                    selectedEndHizb--
                                                    endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                if (selectedEndHizb < 60) {
                                                    selectedEndHizb++
                                                    endPage = QuranStructure.getEndPageForHizb(selectedEndHizb)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }
                            }

                            WirdSelectionMode.AJZAA -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "البدء من: الجزء $selectedStartJuz (ص ${QuranStructure.getStartPageForJuz(selectedStartJuz)})",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Row {
                                        Button(
                                            onClick = {
                                                if (selectedStartJuz > 1) {
                                                    selectedStartJuz--
                                                    startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                if (selectedStartJuz < 30) {
                                                    selectedStartJuz++
                                                    startPage = QuranStructure.getStartPageForJuz(selectedStartJuz)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الانتهاء في: الجزء $selectedEndJuz (ص ${QuranStructure.getEndPageForJuz(selectedEndJuz)})",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Row {
                                        Button(
                                            onClick = {
                                                if (selectedEndJuz > selectedStartJuz) {
                                                    selectedEndJuz--
                                                    endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                if (selectedEndJuz < 30) {
                                                    selectedEndJuz++
                                                    endPage = QuranStructure.getEndPageForJuz(selectedEndJuz)
                                                }
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }
                            }

                            WirdSelectionMode.PAGES -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "صفحة البداية: $startPage", fontSize = 12.sp, color = Color.White)
                                    Row {
                                        Button(
                                            onClick = { if (startPage > 1) startPage-- },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = { if (startPage < 604) startPage++ },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "صفحة النهاية: $endPage", fontSize = 12.sp, color = Color.White)
                                    Row {
                                        Button(
                                            onClick = { if (endPage > startPage) endPage-- },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("-", fontSize = 14.sp) }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = { if (endPage < 604) endPage++ },
                                            shape = CircleShape,
                                            modifier = Modifier.size(28.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) { Text("+", fontSize = 14.sp) }
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

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Repeat Schedule
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
                    val final24Hour = AlarmUiFormatters.toHour24(hour12, isAm)
                    val idToSave = alarmToEdit?.id ?: (System.currentTimeMillis() % 100000).toInt()
                    val savedAlarm = AlarmItem(
                        id = idToSave,
                        hour = final24Hour,
                        minute = minute,
                        repeatDays = repeatDays,
                        isEnabled = true,
                        isVibrationEnabled = isVibrate,
                        label = label.ifBlank { "ورد القرآن الكريم" },
                        targetPage = startPage,
                        startPage = startPage,
                        pageCount = calculatedPageCount,
                        volumeLevel = volumeLevel.toInt(),
                        isSequential = isSequential
                    )
                    onSave(savedAlarm)
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
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color(0xFF94A3B8))
            }
        }
    )
}
