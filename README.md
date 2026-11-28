# منبه القران الكريم (Quran Alarm) — Android & iOS

تطبيق مفتوح المصدر لنظامي **Android** و**iOS (iPhone)** يربط إيقاف المنبه بقراءة الورد اليومي المحدد من القرآن الكريم، ويعمل دون اتصال بالإنترنت وبلا إعلانات أو أدوات تتبع.

---

## الميزات الرئيسية

- **عرض مصحف المدينة المنورة (604 صفحات):**
  - عرض صفحات المصحف بصيغة WebP عالية الدقة (15 سطراً ثابتاً في الصفحة باستثناء صفحتي الفاتحة وأول البقرة).
  - قاعدة بيانات SQLite مدمجة (`ayahinfo.db`) لتحديد إحداثيات السطور وتظليل السطر المقروء أثناء التلاوة.
- **مرونة تحديد الورد اليومي:**
  - اختيار الورد بالسور (مع البحث بالاسم أو الرقم)، أو بالأحزاب (60 حزباً)، أو بالأجزاء (30 جزءاً)، أو بنطاق الصفحات، أو بعدد الآيات داخل السورة الواحدة.
  - دعم **الورد المتسلسل** (الانتقال تلقائياً إلى الجزء التالي بعد إتمام القراءة) أو **الورد الثابت**.
- **ضبط المنبه والصوت:**
  - اختيار الوقت بعجلات تمرير (12 ساعة مع ص/م) وحساب الوقت المتبقي حتى موعد الرنين.
  - أربع نغمات مدمجة (`أذان الفجر`، `تكبيرات العيد`، `نغمة هادئة`، `تنبيه قرآني`) إضافةً إلى إمكانية اختيار ملف صوتي محلي.
  - إمكانية التأجيل لمدة 5 دقائق من شاشة الرنين، وكتم الصوت والاهتزاز فور بدء قراءة الورد.
- **الموثوقية والخصوصية:**
  - يعمل **100% بدون إنترنت** (`Offline / Air-Gapped`): بدون إذن `INTERNET` على أندرويد، ومع `NSAllowsArbitraryLoads = false` وملف `PrivacyInfo.xcprivacy` على iOS.
  - على أندرويد: جدولة عبر `AlarmManager.setAlarmClock()` مع إعادة الجدولة بعد إعادة التشغيل ودعم `Direct Boot` ومعالج أذونات الشركات المصنعة.
  - على الآيفون (iOS): جدولة إشعارات حساسة للوقت (`Time-Sensitive Notifications`) مع نغمات `.caf` مدمجة وتشغيل صوتي عبر `AVAudioSession (.playback)` وإبقاء الشاشة مضاءة أثناء التلاوة.

---

## نسخة الآيفون (iOS) — البنية والتشغيل للمطورين

تتشارك نسخة iOS نفس المصحف الشريف (`604` صفحة WebP + `ayahinfo.db`) وخطوط `Cairo` والنغمات الصوتية مباشرة من مجلدات أندرويد عبر سكربت التحضير، دون تضخيم المستودع بتكرار الملفات الثنائية.

### المتطلبات (macOS)
- Xcode 15+ مع iOS Simulator
- [XcodeGen](https://github.com/yonaskool/XcodeGen) (`brew install xcodegen`)
- [FFmpeg](https://ffmpeg.org/) (`brew install ffmpeg`)

### أوامر التحضير والبناء والاختبار (iOS)

1. **تحضير الأصول المشتركة وتوليد مشروع Xcode:**
   ```bash
   chmod +x ios/scripts/*.sh
   ./ios/scripts/prepare_ios_resources.sh
   (cd ios && xcodegen generate)
   ```

2. **تشغيل اختبارات الوحدة (`XCTest`) وواجهة المستخدم (`XCUITest`) على محاكي iPhone:**
   ```bash
   xcodebuild test \
     -project ios/QuranAlarm.xcodeproj \
     -scheme QuranAlarm \
     -destination 'platform=iOS Simulator,name=iPhone 16'
   ```

3. **بناء حزمة IPA غير موقعة لأجهزة iOS:**
   ```bash
   xcodebuild build \
     -project ios/QuranAlarm.xcodeproj \
     -scheme QuranAlarm \
     -configuration Release \
     -sdk iphoneos \
     -derivedDataPath ios/DerivedData \
     CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
   ./ios/scripts/package_unsigned_ipa.sh \
     ios/DerivedData/Build/Products/Release-iphoneos/QuranAlarm.app \
     ios/build/QuranAlarm-unsigned.ipa
   ```

---

## نسخة أندرويد (Android) — البناء والاختبار للمطورين

### التقنيات والمتطلبات
- **اللغة والواجهات:** Kotlin، Jetpack Compose، Material 3 (Android 8.0 API 26 إلى Android 15 API 35).
- **المتطلبات:** JDK 17 وAndroid SDK (API 35).

### الأوامر

1. **تشغيل اختبارات الوحدة:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

2. **بناء نسخة Debug:**
   ```bash
   ./gradlew assembleDebug
   ```
   *(الناتج في `app/build/outputs/apk/debug/app-debug.apk`)*

3. **فحص الـ Lint والبناء:**
   ```bash
   ./gradlew check
   ```

4. **بناء نسخة Release موقعة محلياً:**
   أنشئ ملف `keystore.properties` في جذر المشروع (مستبعد تلقائياً في `.gitignore`):
   ```properties
   storeFile=your-release-key.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=YOUR_KEY_ALIAS
   keyPassword=YOUR_KEY_PASSWORD
   ```
   ثم نفّذ:
   ```bash
   ./gradlew assembleRelease
   ```
   *(الناتج في `app/build/outputs/apk/release/app-release.apk`)*

---

## خارطة الطريق

راجع ملف [roadmap.md](roadmap.md) للاطلاع على مراحل تنفيذ نسخة iOS وحالة الإنجاز والاختبار.

---

## الترخيص

هذا المشروع مرخص بموجب رخصة [MIT License](LICENSE).
