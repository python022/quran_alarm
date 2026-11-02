package com.quran.alarm

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import com.quran.alarm.alarm.data.scheduler.AlarmSchedulerImpl
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.model.QuranStructure
import com.quran.alarm.ui.AlarmUiFormatters
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var scheduler: AlarmSchedulerImpl
    private lateinit var repository: AlarmRepositoryImpl

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
                        onShowToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmMainScreen(
    repository: AlarmRepositoryImpl,
    scheduler: AlarmSchedulerImpl,
    onShowToast: (String) -> Unit
) {
    var alarmList by remember { mutableStateOf(repository.getAllAlarms()) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refreshAlarms() {
        alarmList = repository.getAllAlarms()
    }

    val activeAlarms = alarmList.filter { it.isEnabled }
    val nextAlarmCountdown = if (activeAlarms.isNotEmpty()) {
        val now = System.currentTimeMillis()
        val nearest = activeAlarms.minOf { it.getNextTriggerMillis(now) }
        AlarmUiFormatters.formatNextAlarmCountdown(nearest, now)
    } else {
        "لا توجد منبهات نشطة"
    }

    Scaffold(
        containerColor = Color(0xFF0F172A),
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
                            text = nextAlarmCountdown,
                            fontSize = 13.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Medium
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
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة منبه"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Quick Test Card for Doze Mode & Wake-up Verification
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "⚡ فحص فائق الدقة (وضع السكون Doze Mode)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "جدولة منبه اختباري بعد 10 ثوانٍ للتحقق من الاستيقاظ التام والاهتزاز والرنين وقفل الأزرار.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val testTime = System.currentTimeMillis() + 10_000L
                            val testAlarm = AlarmItem(
                                id = 999,
                                hour = Calendar.getInstance().apply { timeInMillis = testTime }.get(Calendar.HOUR_OF_DAY),
                                minute = Calendar.getInstance().apply { timeInMillis = testTime }.get(Calendar.MINUTE),
                                repeatDays = emptySet(),
                                isEnabled = true,
                                isVibrationEnabled = true,
                                label = "تجربة الاستيقاظ والورد المتسلسل (10 ثوانٍ)",
                                targetPage = 42,
                                startPage = 42,
                                pageCount = 5,
                                volumeLevel = 100,
                                explicitTriggerMillis = testTime
                            )
                            repository.saveAlarm(testAlarm)
                            scheduler.schedule(testAlarm)
                            refreshAlarms()
                            onShowToast("تمت جدولة المنبه الاختباري بعد 10 ثوانٍ! يمكنك إطفاء الشاشة الآن.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text(
                            text = "تشغيل منبه اختباري (بعد 10 ثوانٍ)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "قائمة المنبهات (${alarmList.size})",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

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
                                scheduler.cancel(alarm)
                                repository.deleteAlarm(alarm.id)
                                refreshAlarms()
                                onShowToast("تم حذف المنبه")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlarmDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newAlarm ->
                repository.saveAlarm(newAlarm)
                scheduler.schedule(newAlarm)
                refreshAlarms()
                showAddDialog = false
                onShowToast("تم حفظ المنبه بنجاح!")
            }
        )
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = AlarmUiFormatters.formatTime12Hour(alarm.hour, alarm.minute),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) Color.White else Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${AlarmUiFormatters.formatRepeatDays(alarm.repeatDays)} • ${alarm.label}",
                    fontSize = 13.sp,
                    color = if (alarm.isEnabled) Color(0xFF94A3B8) else Color(0xFF475569)
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "الورد: صفحة ${alarm.startPage} إلى ${alarm.endPage} (${alarm.pageCount} صفحات) • صوت ${alarm.volumeLevel}%${if (alarm.isVibrationEnabled) " • اهتزاز" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.isEnabled) Color(0xFF38BDF8) else Color(0xFF475569)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

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
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit
) {
    var hour by remember { mutableIntStateOf(5) }
    var minute by remember { mutableIntStateOf(0) }
    var isAm by remember { mutableStateOf(true) }
    var repeatDays by remember { mutableStateOf(setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)) }
    var isVibrate by remember { mutableStateOf(true) }
    var label by remember { mutableStateOf("صلاة الفجر والورد القرآني") }
    var selectedJuz by remember { mutableIntStateOf(3) }
    var startPage by remember { mutableIntStateOf(QuranStructure.getStartPageForJuz(3)) }
    var pageCount by remember { mutableIntStateOf(5) }
    var volumeLevel by remember { mutableFloatStateOf(100f) }

    val daysList = listOf(
        Calendar.SATURDAY to "السبت",
        Calendar.SUNDAY to "الأحد",
        Calendar.MONDAY to "الاثنين",
        Calendar.TUESDAY to "الثلاثاء",
        Calendar.WEDNESDAY to "الأربعاء",
        Calendar.THURSDAY to "الخميس",
        Calendar.FRIDAY to "الجمعة"
    )

    val endPageCalc = ((startPage + pageCount - 2) % 604) + 1
    val nextDayStartCalc = ((startPage + pageCount - 1) % 604) + 1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "ضبط منبه الورد القرآني",
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
                // 1. Time selector
                Text(
                    text = "وقت المنبه",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { hour = if (hour == 1) 12 else hour - 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("-", fontSize = 18.sp)
                        }
                        Text(
                            text = String.format("%02d", hour),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Button(
                            onClick = { hour = if (hour == 12) 1 else hour + 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 18.sp)
                        }
                    }

                    Text(
                        text = ":",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    // Minute selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { minute = if (minute == 0) 55 else minute - 5 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
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
                            onClick = { minute = if (minute >= 55) 0 else minute + 5 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("+", fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // AM/PM Toggle
                    Button(
                        onClick = { isAm = !isAm },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAm) Color(0xFF10B981) else Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isAm) "ص" else "م",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Quran Wird Section (الورد القرآني المتسلسل)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📖 خطة الورد اليومي المتسلسل",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Select by Juz
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "البدء من الجزء: $selectedJuz",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Row {
                                Button(
                                    onClick = {
                                        if (selectedJuz > 1) {
                                            selectedJuz--
                                            startPage = QuranStructure.getStartPageForJuz(selectedJuz)
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("-")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = {
                                        if (selectedJuz < 30) {
                                            selectedJuz++
                                            startPage = QuranStructure.getStartPageForJuz(selectedJuz)
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("+")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Daily pages amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مقدار الورد اليومي: $pageCount صفحات",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Row {
                                Button(
                                    onClick = { if (pageCount > 1) pageCount-- },
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("-")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = { if (pageCount < 50) pageCount++ },
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("+")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live summary card
                        Text(
                            text = "ورد اليوم الأول: من صفحة $startPage إلى صفحة $endPageCalc ($pageCount صفحات)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "اليوم التالي: سينتقل تلقائياً إلى صفحة $nextDayStartCalc",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Audio & Volume Control (درجة الصوت)
                Text(
                    text = "درجة الصوت: ${volumeLevel.toInt()}%",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Slider(
                    value = volumeLevel,
                    onValueChange = { volumeLevel = it },
                    valueRange = 10f..100f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF10B981),
                        activeTrackColor = Color(0xFF10B981),
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Vibration Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الاهتزاز المتواصل",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Switch(
                        checked = isVibrate,
                        onCheckedChange = { isVibrate = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Repeat schedule
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

                Spacer(modifier = Modifier.height(6.dp))

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

                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("الوصف") },
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
                    val final24Hour = if (isAm) {
                        if (hour == 12) 0 else hour
                    } else {
                        if (hour == 12) 12 else hour + 12
                    }
                    val newId = (System.currentTimeMillis() % 100000).toInt()
                    val newAlarm = AlarmItem(
                        id = newId,
                        hour = final24Hour,
                        minute = minute,
                        repeatDays = repeatDays,
                        isEnabled = true,
                        isVibrationEnabled = isVibrate,
                        label = label.ifBlank { "ورد القرآن الكريم" },
                        targetPage = startPage,
                        startPage = startPage,
                        pageCount = pageCount,
                        volumeLevel = volumeLevel.toInt()
                    )
                    onSave(newAlarm)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color(0xFF94A3B8))
            }
        }
    )
}
