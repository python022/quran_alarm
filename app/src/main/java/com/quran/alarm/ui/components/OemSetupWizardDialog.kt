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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.alarm.alarm.data.device.DeviceReadinessStatus
import com.quran.alarm.alarm.data.device.OemDeviceHelper

@Composable
fun OemSetupWizardDialog(
    status: DeviceReadinessStatus,
    onDismiss: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Column {
                Text(
                    text = "معالج موثوقية المنبه",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = status.oemFamily.displayNameArabic,
                    color = Color(0xFF38BDF8),
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = status.oemFamily.guidanceArabic,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = "قسم أساسي (مطلوب لعمل المنبه الصارم)",
                    color = Color(0xFF38BDF8),
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
                    color = Color(0xFF94A3B8),
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Text("فتح إعدادات التشغيل التلقائي للشركة المصنعة", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("تم", fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
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
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isGranted) "مفعّل ✓" else "يتطلب تفعيل",
                    color = if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontSize = 11.sp
                )
            }
            if (!isGranted) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        }
    }
}
