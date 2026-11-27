package com.quran.alarm.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.data.device.DeviceReadinessStatus
import com.quran.alarm.alarm.data.device.OemDeviceHelper
import com.quran.alarm.ui.theme.QuranColors

@Composable
fun OemSetupWizardDialog(
    status: DeviceReadinessStatus,
    onDismiss: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuranColors.BgCard,
        title = {
            Column {
                Text(
                    text = "معالج موثوقية المنبه",
                    color = QuranColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = status.oemFamily.displayNameArabic,
                    color = QuranColors.GoldBadgeText,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = QuranColors.BgElevated),
                    border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = status.oemFamily.guidanceArabic,
                        color = QuranColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = "قسم أساسي (مطلوب لعمل المنبه الصارم)",
                    color = QuranColors.ActivePillText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                ReadinessStepRow(
                    title = "إذن الإشعارات",
                    isGranted = status.isNotificationsGranted,
                    actionLabel = "تفعيل",
                    onAction = onRequestNotifications
                )

                ReadinessStepRow(
                    title = "عرض ملء الشاشة",
                    isGranted = status.isFullScreenIntentGranted,
                    actionLabel = "فتح الإعدادات",
                    onAction = { OemDeviceHelper.openFullScreenIntentSettings(context) }
                )

                ReadinessStepRow(
                    title = "وضع الإلزام (الظهور فوق التطبيقات)",
                    isGranted = status.isOverlayGranted,
                    actionLabel = "فتح الإعدادات",
                    onAction = { OemDeviceHelper.openOverlaySettings(context) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "قسم تحسين الأداء (اختياري)",
                    color = QuranColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                ReadinessStepRow(
                    title = "استثناء موفر البطارية",
                    isGranted = status.isBatteryOptimizationIgnored,
                    actionLabel = "تفعيل",
                    onAction = { OemDeviceHelper.openBatteryOptimizationSettings(context) }
                )

                if (status.oemFamily.supportsAutoStartSettings) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val openedOemScreen = OemDeviceHelper.openOemAutoStartSettings(context)
                            if (!openedOemScreen) {
                                Toast.makeText(
                                    context,
                                    "تعذر فتح القائمة مباشرة؛ ${status.oemFamily.guidanceArabic}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, QuranColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuranColors.ActivePillText)
                    ) {
                        Text("فتح إعدادات التشغيل التلقائي للشركة المصنعة", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuranColors.CtaBg,
                    contentColor = QuranColors.CtaText
                )
            ) {
                Text("تم", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun ReadinessStepRow(
    title: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = QuranColors.BgElevated),
        border = BorderStroke(1.dp, QuranColors.BorderSubtle),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = QuranColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isGranted) "مفعّل ✓" else "يتطلب تفعيل",
                    color = if (isGranted) QuranColors.ActivePillText else QuranColors.WarningOrange,
                    fontSize = 11.sp
                )
            }
            if (!isGranted) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = QuranColors.ActivePillText, fontSize = 12.sp)
                }
            }
        }
    }
}
