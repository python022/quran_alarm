# خارطة طريق بناء نسخة الآيفون (iOS) — منبه القرآن الكريم

خارطة طريق مرحلية لبناء التطبيق الأصلي لنظام **iOS (iPhone)** بلغة **Swift 5 + SwiftUI** مع الحفاظ التام على نسخة الأندرويد الحالية، ومشاركة المصحف والخطوط والصوتيات دون تضخيم المستودع، والعمل دون اتصال بالإنترنت (100% Offline & Zero-Network).

---

## نظرة عامة على المراحل

| المرحلة | الوصف | الحالة |
| :--- | :--- | :--- |
| المرحلة 1 | تأسيس مشروع iOS والهوية البصرية والخطوط والأصول المشتركة | ✅ مكتملة |
| المرحلة 2 | طبقة البيانات وبنية المصحف الشريف ومحرك الصفحات والأسطر | ✅ مكتملة |
| المرحلة 3 | منطق المنبه والتخزين والجدولة والصوت لنظام iOS | ✅ مكتملة |
| المرحلة 4 | واجهات المستخدم الكاملة بلغة SwiftUI وإدارة الحالة | ✅ مكتملة |
| المرحلة 5 | الاختبارات الشاملة (`XCTest` & `XCUITest`) وخط أنابيب GitHub Actions لنظام iOS | ✅ مكتملة |
| المرحلة 6 | الرفع والاختبار الفعلي على GitHub Actions والتحقق من نجاح جميع الفحوصات | ✅ مكتملة |

---

## المرحلة 1: تأسيس مشروع iOS والهوية البصرية والخطوط والأصول المشتركة
- [x] تحديث `.gitignore` لاستبعاد ملفات Xcode المولدة (`ios/GeneratedResources/`، `ios/*.xcodeproj`، `ios/build/`، `ios/DerivedData/`) وعدم تكرار الأصول الثقيلة (160 ميجابايت) داخل Git.
- [x] إنشاء أيقونة التطبيق لنظام iOS بمقاس `1024×1024` (RGB بدون شفافية) في `ios/QuranAlarm/Assets.xcassets/AppIcon.appiconset/AppIcon.png`.
- [x] إنشاء `ios/QuranAlarm/Info.plist` (إعدادات العربية والوضع الداكن والخطوط المضمنة وصوت الخلفية وحظر الشبكة `NSAllowsArbitraryLoads = false`) وملف الخصوصية `ios/QuranAlarm/PrivacyInfo.xcprivacy`.
- [x] إنشاء السكربتات التنفيذية (`ios/scripts/prepare_ios_resources.sh` لنسخ صفحات المصحف الـ 604 وقاعدة بيانات `ayahinfo.db` وخطوط Cairo وتحويل النغمات وإنتاج ملفات `.caf` ≤ 28 ثانية لإشعارات iOS، و`ios/scripts/package_unsigned_ipa.sh` لتجميع `.ipa`).
- [x] إنشاء `ios/project.yml` لـ XcodeGen (أهداف `QuranAlarm`، `QuranAlarmTests`، `QuranAlarmUITests` ومخطط الاختبار المشترك).
- [x] بناء نظام التصميم الموحد في `ios/QuranAlarm/Theme/` (`QuranColors.swift`، `QuranTypography.swift` بالأسماء الدقيقة لخطوط Cairo، و`QuranComponents.swift` للكبسولات والبطاقات الذهبية والقوائم القابلة للطي).

## المرحلة 2: طبقة البيانات وبنية المصحف الشريف ومحرك الصفحات والأسطر
- [x] نقل بنية القرآن الكريم (`QuranStructure.swift`) كاملة: 114 سورة، 30 جزءاً، 60 حزباً، وحسابات التوقع للختمة والورد، ونظام الآيات داخل السورة الواحدة.
- [x] بناء نماذج الإحداثيات (`LineRect.swift`، `NormalizedLineRect.swift`) ومحرك التحويل (`CoordinateNormalizer.swift`) مع دعم FitCenter والتقاط السطر باللمس.
- [x] بناء محرك قاعدة البيانات المحلية (`AyahInfoDatabase.swift` عبر `SQLite3`) والمستودع (`LineBoundsRepository.swift`) مع خوارزمية استيفاء السطور المفقودة وفصلها الرأسي الصارم وتصنيف `SURAH_HEADER` و`BASMALAH`.
- [x] بناء مدير صفحات المصحف (`QuranPageManager.swift`) لفك تشفير صور WebP عبر `ImageIO` مع ذاكرة LRU محدودة بـ 32 ميجابايت والتحميل المسبق للصفحات المجاورة.
- [x] بناء شاشة عرض صفحة المصحف (`QuranPageView.swift`) بخلفية `#FFFDF5` وتظليل السطر المقروء وتجاهل عناوين السور.

## المرحلة 3: منطق المنبه والتخزين والجدولة والصوت لنظام iOS
- [x] نقل نموذج المنبه (`AlarmItem.swift`) ومنطق الورد المتسلسل والثابت وحساب الموعد القادم باستخدام التقويم الميلادي (`Calendar(identifier: .gregorian)`) لضمان استقرار أيام الأسبوع.
- [x] بناء إعدادات النغمات المدمجة (`AlarmSoundPresets.swift`) ومحرك استيراد الملفات الصوتية المحلية (`CustomSoundStorage.swift`) مع حدود الحجم 15 ميجابايت ورفض أي روابط شبكية.
- [x] بناء مستودع المنبهات (`AlarmRepository.swift`) ومخزن استئناف الجلسة (`AlarmSessionStore.swift`) فوق `UserDefaults`.
- [x] بناء مدير الصوت والاهتزاز (`SmartAlarmAudioManager.swift`) عبر `AVAudioSession (.playback)` و`AVAudioPlayer` مع كتم الصوت فوراً عند بدء القراءة.
- [x] بناء مجدول إشعارات iOS الحساسة للوقت (`IOSAlarmScheduler.swift` عبر `UNUserNotificationCenter` وفئة `QURAN_ALARM_CATEGORY` مع تنبيهات متتابعة) وفاحص الجاهزية (`IOSDeviceReadinessHelper.swift`).

## المرحلة 4: واجهات المستخدم الكاملة بلغة SwiftUI وإدارة الحالة
- [x] نقل منسقات النصوص العربية والأرقام (`AlarmUiFormatters.swift`) مع الحفاظ على أرقام الساعة الإنجليزية (`en_US_POSIX`).
- [x] بناء منسق الجلسة وإدارة الحالة (`AlarmViewModel.swift`، `AlarmAlertViewModel.swift`، `AlarmSessionCoordinator.swift`) مع قفل زر الإغلاق الصارم حتى إتمام الورد كاملاً.
- [x] بناء واجهات SwiftUI كاملة باتجاه RTL والوضع الداكن:
  - `AlarmListView.swift` و`AlarmCardView.swift` و`IOSReliabilityWizardSheet.swift`.
  - `TimeWheelPickerView.swift` و`SurahSearchPickerSheet.swift`.
  - `AddEditAlarmView.swift` و`AddEditAlarmSections.swift` (الأوضاع الأربعة، العجلات المترابطة، وضع الآيات، الاختصارات السريعة، المعاينة الحية، الاستيراد الصوتي، وحوارات التأكيد والحذف).
  - `AlarmAlertView.swift` (مرحلة الرنين ومرحلة تلاوة المصحف الكاملة مع بقاء الشاشة مضاءة).
  - `QuranAlarmApp.swift` مع دعم أعلام الاختبار `-UITestResetState` و`-UITestMode`.

## المرحلة 5: الاختبارات الشاملة (`XCTest` & `XCUITest`) وخط أنابيب GitHub Actions لنظام iOS
- [x] كتابة اختبارات الوحدة (`QuranAlarmTests`):
  - `DesignSystemAndGuardsTests.swift`
  - `QuranStructureAndWirdTests.swift`
  - `AyahInfoDatabaseTests.swift` (اختبار جميع الصفحات الـ 604 بدون تداخل)
  - `CoordinateNormalizerTests.swift`
  - `QuranPageManagerTests.swift`
  - `AlarmFormattersAndRecurrenceTests.swift`
  - `AlarmAlertViewModelTests.swift`
  - `AudioAssetsIntegrityTests.swift`
- [x] كتابة اختبارات واجهة المستخدم على محاكي iPhone (`QuranAlarmUITests.swift`).
- [x] إنشاء ملف `.github/workflows/ios-ci.yml` للتحقق من الخصوصية وانعدام الشبكة وتشغيل اختبارات المحاكي وبناء `QuranAlarm-unsigned.ipa`.
- [x] تحديث `README.md` لتوثيق نسخة iOS بجانب نسخة أندرويد.

## المرحلة 6: الرفع والاختبار الفعلي على GitHub Actions والتحقق من نجاح جميع الفحوصات
- [x] مراجعة النظافة والملفات المرفوعة عبر `git status` للتأكد من خلو المستودع من الملفات المولدة الثقيلة.
- [x] رفع الفرع `ios-port` ومراقبة تشغيل `iOS CI & Simulator Quality Gates` على GitHub Actions ومعالجة أي ملاحظات حتى النجاح التام (`46104020937`).
- [x] دمج العمل في `main` وتحديث حالة جميع المراحل والتحقق من نجاح مساري iOS وAndroid CI على `main`.
