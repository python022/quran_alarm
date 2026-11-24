# Keep Android Manifest-declared entry points
-keep public class com.quran.alarm.MainActivity
-keep public class com.quran.alarm.ui.AlarmAlertActivity
-keep public class com.quran.alarm.alarm.receiver.AlarmReceiver
-keep public class com.quran.alarm.alarm.receiver.BootReceiver
-keep public class com.quran.alarm.alarm.receiver.TimeAndPackageChangeReceiver
-keep public class com.quran.alarm.alarm.service.AlarmForegroundService

# Keep ViewModels instantiated by ViewModelProvider reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
