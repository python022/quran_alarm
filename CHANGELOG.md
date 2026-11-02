# سجل التغييرات (CHANGELOG)
جميع التغييرات الموثقة لمشروع **منبه القرآن الكريم (Quran Alarm)** وفقاً لميثاق [AGENTS.md](AGENTS.md) وخارطة الطريق [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [غير مطلق - Unreleased]

### 🎯 المرحلة 1: محرك المنبه فائق الموثوقية والرنين الخلفي (Reliable Alarm Core)
- **GitHub Issue:** [#1](https://github.com/python022/quran_alarm/issues/1) - توثيق كامل لأهداف ومعايير نجاح وخطة الـ Pull Requests الخاصة بالمرحلة 1.
- **الخطة الهندسية لطلبات الدمج (Atomic PRs):**
  - [x] **تأسيس خطة العمل وفتح Issue:** تم فتح Issue #1 وتفصيل الـ 6 PRs ومعايير القبول.
  - [x] **PR 1:** `feat(build): initialize Android Gradle Kotlin DSL project structure with zero-network manifest` (تم الدمج بنجاح عبر PR #2)
  - [x] **PR 2:** `feat(alarm): implement AlarmScheduler domain models and AlarmSchedulerImpl with unit tests` (تم الدمج بنجاح عبر PR #3)
  - [x] **PR 3:** `feat(service): implement AlarmReceiver and AlarmForegroundService with mediaPlayback` (تم الدمج بنجاح عبر PR #4)
  - [x] **PR 4:** `feat(audio): implement SmartAlarmAudioManager for STREAM_ALARM playback and volume lifecycle` (تم الدمج بنجاح عبر PR #5)
  - [ ] **PR 5:** `feat(boot): implement BootReceiver for alarm rescheduling after device restart`
  - [ ] **PR 6:** `feat(ui): implement AlarmAlertActivity with lockscreen display flags and Compose UI`

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
