# سجل التغييرات (CHANGELOG)
جميع التغييرات الموثقة لمشروع **منبه القرآن الكريم (Quran Alarm)** وفقاً لميثاق [AGENTS.md](AGENTS.md) وخارطة الطريق [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [1.0.0-phase1] - 2026-11-02

### 🎯 المرحلة 1: محرك المنبه فائق الموثوقية والرنين الخلفي (Reliable Alarm Core) - مكتملة بنجاح 100%
- **GitHub Issue:** [#1](https://github.com/python022/quran_alarm/issues/1) - إغلاق كافة أهداف ومتطلبات المرحلة الأولى.
- **طلبات الدمج المكتملة (Merged PRs):**
  - [x] **PR 1 (#2):** `feat(build): initialize Android Gradle Kotlin DSL project structure with zero-network manifest`
  - [x] **PR 2 (#3):** `feat(alarm): implement AlarmScheduler domain models and AlarmSchedulerImpl with unit tests`
  - [x] **PR 3 (#4):** `feat(service): implement AlarmReceiver and AlarmForegroundService with mediaPlayback`
  - [x] **PR 4 (#5):** `feat(audio): implement SmartAlarmAudioManager for STREAM_ALARM playback and volume lifecycle`
  - [x] **PR 5 (#6):** `feat(boot): implement BootReceiver for alarm rescheduling after device restart`
  - [x] **PR 6 (#7):** `feat(ui): implement AlarmAlertActivity with lockscreen display flags and Compose UI`
  - [x] **PR 7 (#8):** `fix(alarm): add USE_FULL_SCREEN_INTENT and direct lockscreen activity launch for Android 14+`
- **التجربة الحية على الجهاز الواقعي (Samsung Galaxy S24 Ultra - Android 16):**
  - تم تثبيت ملف الـ APK واختبار الجدولة والانطلاق في الموعد المحدد بالثانية.
  - تم اختبار استيقاظ المنبه وانطلاق الصوت وشاشة التنبيه `AlarmAlertActivity` فوق القفل بنجاح كامل.

---

## [0.1.0] - 2026-11-02

### 📍 المرحلة 0: التأسيس والبنية التحتية وملفات الأساس (Foundation & CI)
- **التوثيق المعماري وميثاق الوكلاء:**
  - اعتماد ميثاق [AGENTS.md](AGENTS.md) الصارم بالقوانين السبع غير القابلة للتفاوض.
  - إعداد وثيقة متطلبات المنتج [docs/PRD.md](docs/PRD.md).
  - إعداد وثيقة البنية المعمارية [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
  - إعداد خارطة الطريق وشروط النجاح [docs/ROADMAP.md](docs/ROADMAP.md).
  - توثيق قرارات المعمارية الخمسة الأولى (`ADR-001` إلى `ADR-005`).
- **مسار التكامل المستمر (CI/CD):**
  - بناء `.github/workflows/ci.yml` للتحقق التلقائي من العزل الشبكي (Zero-Internet) وخلو الصلاحيات الخطرة.
  - إعداد خطاف الحماية `.githooks/pre-push` لحظر الدفع المباشر لفرع `main`.
