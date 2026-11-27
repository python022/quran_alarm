package com.quran.alarm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.MainActivity
import com.quran.alarm.R
import com.quran.alarm.alarm.data.device.OemDeviceHelper
import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.ui.viewmodel.AlarmUiState
import com.quran.alarm.ui.theme.GoldBadgeSurface
import com.quran.alarm.ui.theme.QuranColors

/**
 * Main Alarm List Screen composed with Dark Slate + Emerald + Gold Design System.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    uiState: AlarmUiState,
    onAddClick: () -> Unit,
    onAlarmClick: (AlarmItem) -> Unit,
    onToggleAlarm: (AlarmItem, Boolean) -> Unit,
    onFixMissingCorePermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOemWizard by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val uniqueAlarms = remember(uiState.alarms) { uiState.alarms.distinctBy { it.id } }
    val needsOemAttention = uiState.deviceReadinessStatus?.needsAttention == true
    val hasEnabledAlarm = uniqueAlarms.any { it.isEnabled }
    val readiness = uiState.deviceReadinessStatus
    val missingFullScreen = readiness != null && !readiness.isFullScreenIntentGranted
    val missingOverlay = readiness != null && !readiness.isOverlayGranted
    val showCorePermissionsBanner = hasEnabledAlarm && (missingFullScreen || missingOverlay)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "منبه القرآن الكريم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = QuranColors.TextPrimary
                        )
                        val hasActiveCountdown = uiState.nextAlarmCountdown.isNotBlank() &&
                            !uiState.nextAlarmCountdown.contains("لا توجد")
                        if (hasActiveCountdown) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = QuranColors.ActivePillBg,
                                border = BorderStroke(1.dp, QuranColors.ActivePillBorder)
                            ) {
                                Text(
                                    text = "⏰ ${uiState.nextAlarmCountdown}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = QuranColors.ActivePillText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "لا توجد منبهات نشطة",
                                fontSize = 12.sp,
                                color = QuranColors.TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = QuranColors.BgElevated
                ),
                actions = {
                    IconButton(onClick = { showOemWizard = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "معالج موثوقية الجهاز",
                            tint = if (needsOemAttention) QuranColors.WarningOrange else QuranColors.TextSecondary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.navigationBarsPadding(),
                onClick = onAddClick,
                containerColor = QuranColors.CtaBg,
                contentColor = QuranColors.CtaText
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة منبه"
                )
            }
        },
        containerColor = QuranColors.BgPrimary
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 0. Core Alert Permissions Guidance Banner (only when at least one alarm is enabled)
            if (showCorePermissionsBanner) {
                val bannerTitle = when {
                    missingFullScreen && missingOverlay -> "مطلوب تفعيل إذنين أساسيين لعمل المنبه"
                    missingFullScreen -> "مطلوب تفعيل إذن عرض ملء الشاشة"
                    else -> "مطلوب تفعيل إذن الظهور فوق التطبيقات"
                }
                val bannerBody = when {
                    missingFullScreen && missingOverlay ->
                        "لضمان فتح شاشة المنبه بملء الشاشة وتشغيل وضع الإلزام عند الرنين، يرجى تفعيل «عرض ملء الشاشة» ثم «الظهور فوق التطبيقات»."
                    missingFullScreen ->
                        "لضمان فتح شاشة المنبه تلقائياً على شاشة القفل، يرجى تفعيل إذن عرض ملء الشاشة."
                    else ->
                        "لضمان عمل وضع الإلزام ومنع تخطي الورد القرآني، يرجى تفعيل إذن الظهور فوق التطبيقات."
                }
                val primaryButtonText = when {
                    missingFullScreen && missingOverlay -> "تفعيل عرض ملء الشاشة (1/2)"
                    missingFullScreen -> "تفعيل عرض ملء الشاشة"
                    else -> "تفعيل الظهور فوق التطبيقات"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = QuranColors.WarningOrangeBg),
                    border = BorderStroke(1.dp, QuranColors.WarningOrange.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "💡", fontSize = 16.sp)
                            Text(
                                text = bannerTitle,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = QuranColors.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bannerBody,
                            fontSize = 12.sp,
                            color = QuranColors.TextSecondary,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onFixMissingCorePermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = QuranColors.WarningOrange,
                                    contentColor = QuranColors.CtaText
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Text(
                                    text = primaryButtonText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(
                                onClick = { showOemWizard = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, QuranColors.WarningOrange.copy(alpha = 0.6f)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = "معالج الجهاز",
                                    fontSize = 12.sp,
                                    color = QuranColors.WarningOrange
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 1. Khatma / Surah Wird Progress Bar
            val khatma = uiState.khatmaProgress
            val surahWird = uiState.surahWirdProgress
            if (khatma != null) {
                WirdProgressCard(
                    title = "📖 مسيرة ختمة القرآن الكريم",
                    badgeText = "${khatma.progressPercentage.toInt()}% (صفحة ${khatma.currentStartPage} من 604)",
                    progress = (khatma.progressPercentage / 100f).coerceIn(0f, 1f),
                    summary = khatma.forecastSummary
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (surahWird != null) {
                WirdProgressCard(
                    title = "📖 مسيرة الورد في سورة ${surahWird.surahName}",
                    badgeText = "${surahWird.progressPercentage.toInt()}% (آية ${surahWird.currentStartAyah} من ${surahWird.totalAyahs})",
                    progress = (surahWird.progressPercentage / 100f).coerceIn(0f, 1f),
                    summary = surahWird.forecastSummary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. Alarms List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قائمة المنبهات (${uniqueAlarms.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QuranColors.TextPrimary
                )
                Text(
                    text = "انقر على أي منبه لتعديله",
                    fontSize = 12.sp,
                    color = QuranColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = QuranColors.CtaBg)
                }
            } else if (uniqueAlarms.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = QuranColors.BgCard),
                    border = BorderStroke(1.dp, QuranColors.BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = "منبه القرآن الكريم",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "ابدأ يومك بنور القرآن والبركة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = QuranColors.TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "لم تقم بضبط أي منبه بعد. اضغط على زر (+) في الأسفل لضبط منبه الفجر ووردك القرآني اليومي مع ميزة تتبع التلاوة.",
                            fontSize = 13.sp,
                            color = QuranColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QuranColors.CtaBg,
                                contentColor = QuranColors.CtaText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "إضافة منبه جديد",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 92.dp)
                ) {
                    items(uniqueAlarms, key = { it.id }) { alarm ->
                        AlarmCardItem(
                            alarm = alarm,
                            onClick = { onAlarmClick(alarm) },
                            onToggle = { isEnabled -> onToggleAlarm(alarm, isEnabled) }
                        )
                    }
                }
            }
        }
    }

    if (showOemWizard) {
        OemSetupWizardDialog(
            status = uiState.deviceReadinessStatus ?: OemDeviceHelper.getReadinessStatus(context),
            onDismiss = { showOemWizard = false },
            onRequestNotifications = {
                (context as? MainActivity)?.requestNotificationsPermissionIfNeeded(fromWizard = true)
            }
        )
    }
}

@Composable
private fun WirdProgressCard(
    title: String,
    badgeText: String,
    progress: Float,
    summary: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = QuranColors.BgElevated),
        border = BorderStroke(1.dp, QuranColors.BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = QuranColors.TextPrimary
                )
                Text(
                    text = badgeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QuranColors.ActivePillText
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = QuranColors.CtaBg,
                trackColor = QuranColors.BgPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            GoldBadgeSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuranColors.GoldBadgeText,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
