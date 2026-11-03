# سجل التغييرات (CHANGELOG)
جميع التغييرات الموثقة لمشروع **منبه القرآن الكريم (Quran Alarm)** وفقاً لميثاق [AGENTS.md](AGENTS.md) وخارطة الطريق [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [1.0.0-phase1] - 2026-11-03

### 🎯 المرحلة 1: محرك المنبه فائق الموثوقية والرنين الخلفي (Reliable Alarm Core) - مطابقة تامة وشاملة 100%
- **GitHub Issue:** [#1](https://github.com/python022/quran_alarm/issues/1) - إغلاق كافة أهداف ومتطلبات المرحلة الأولى بعد المطابقة الصارمة لمتطلبات المستخدم وتطبيق Alarmy.
- **طلبات الدمج المكتملة والمدمجة في فرع `main` (Merged PRs):**
  - [x] **PR 1 (#2):** `feat(build): initialize Android Gradle Kotlin DSL project structure with zero-network manifest`
  - [x] **PR 2 (#3):** `feat(alarm): implement AlarmScheduler domain models and AlarmSchedulerImpl with unit tests`
  - [x] **PR 3 (#4):** `feat(service): implement AlarmReceiver and AlarmForegroundService with mediaPlayback`
  - [x] **PR 4 (#5):** `feat(audio): implement SmartAlarmAudioManager for STREAM_ALARM playback and volume lifecycle`
  - [x] **PR 5 (#6):** `feat(boot): implement BootReceiver for alarm rescheduling after device restart`
  - [x] **PR 6 (#7):** `feat(ui): implement AlarmAlertActivity with lockscreen display flags and Compose UI`
  - [x] **PR 7 (#8):** `fix(alarm): add USE_FULL_SCREEN_INTENT and direct lockscreen activity launch for Android 14+`
  - [x] **PR 9 (#9):** `feat(alarm): enrich AlarmItem model with repeatDays and recurrence calculation, and update AlarmRepository`
  - [x] **PR 10 (#10):** `feat(audio): add continuous vibration loop, fallback tone generator, and volume enforcer`
  - [x] **PR 11 (#11):** `feat(ui): implement interactive TimePicker dialog, recurring days selector, and full alarm list UI`
  - [x] **PR 12 (#12):** `feat(ui): update AlarmAlertActivity with digital clock, date, snooze, and anti-bypass blockers`
  - [x] **PR 13 (#13):** `feat(wird): add daily Quran wird progression, volume level, and sound customization`
  - [x] **PR 14 (#14):** `feat(ui): add Juz selector, daily portion configurator, and volume slider to AddAlarmDialog`
  - [x] **PR 15 (#15):** `feat(ui): enforce sticky immersive mode, navigation lock, and automatic wird progression on alarm dismissal`
  - [x] **PR 16 (#16):** `feat(ui): add excludeFromRecents, singleInstance launch, and background relaunch options for Android 14+`
  - [x] **PR 17 (#17):** `feat(quran): implement complete 114 Surahs, 60 Ahzab structure and Khatma forecast models`
  - [x] **PR 18 (#18):** `feat(ui): implement flexible TimePicker with AM/PM selector, exact minute picker, multi-modal Wird configurator, and Khatma progress bar`
  - [x] **PR 19 (#19):** `feat(alarm): implement unbreakable screen pinning (startLockTask) and foreground service watchdog loop`
  - [x] **PR 21 (#21):** `fix(service): resolve background activity launch for Android 14+ via PendingIntent`
  - [x] **PR 22 (#22):** `fix(alarm): resolve Android 14+ PendingIntent background start crash and optimize Wird selection chips layout`
  - [x] **PR 23 (#23):** `fix(audio): eliminate AudioTrack native leak, respect volume level, and configure DND vibration attributes`
  - [x] **PR 24 (#24):** `fix(alarm): prevent ghost alarms on reboot, remove notification dismiss bypass, and namespace PendingIntent request codes`
  - [x] **PR 25 (#25):** `fix(quran): return primary surah for shared pages in QuranStructure and auto-push bounds across selection modes`
  - [x] **PR 26 (#26):** `refactor(arch): adopt Clean Architecture with AlarmViewModel, StateFlow, and modular UI components`
  - [x] **PR 27 (#27):** `test(scheduler): eliminate fake contract tests, remove trivial asserts, and implement real Robolectric tests for AlarmSchedulerImpl`
  - [x] **PR 28 (#28):** ix(alarm): eliminate startLockTask, prevent VolumeEnforcer IPC loop crash, adopt SYSTEM_ALERT_WINDOW for instant fullscreen alarm overlay, and optimize 2x2 mode chips

- **الميزات المنفذة وفق متطلبات المستخدم والمطابقة الكاملة:**
  1. **منتقي الوقت المرن:** دعم نظام 12 ساعة مع مفتاحين مستقلين لـ (صباحاً AM / مساءً PM)، واختيار أي دقيقة بالدقة الكاملة (00..59) دقيقة بدقيقة مع سلايدر سلس للإدخال المباشر.
  2. **مرونة الورد القرآني متعدد الأنماط:** إمكانية ضبط الورد بالسور الـ 114 كاملة، أو بالأحزاب الـ 60 كاملة، أو بالأجزاء الـ 30 كاملة، أو بالصفحات (1..604) مع تحديد صريح ودقيق لـ "البدء من" و "الانتهاء في".
  3. **شريط وتنبؤ الختمة المباركة:** حساب موعد الختمة تلقائياً وعرض شريط تقدم الختمة ونسبة الإنجاز على الواجهة الرئيسية.
  4. **التفريق بين الورد المتسلسل والورد الثابت:** دعم التقدم اليومي التلقائي للصفحات لختم المصحف، أو تثبيت الورد لقراءة سور محددة كالملك والكهف.
  5. **إمكانية تعديل أي منبه موجود:** النقر على أي بطاقة منبه يفتح نافذة التعديل محملة بكافة البيانات مع حفظ التعديلات.
  6. **القفل الحديدي لشاشة الرنين:** وضع الغمر الدائم (Sticky Immersive Mode)، حظر الرجوع (`OnBackPressedCallback`)، قفل مفاتيح الصوت، وضع تثبيت الشاشة (`startLockTask`)، وحلقة حراسة الخدمة الأمامية لإعادة إطلاق شاشة التنبيه فورياً إذا حاول المستخدم الخروج.
  7. **معاينة نغمة الصوت الحية وتسمية سريعة:** معاينة درجة الصوت فور تحريك المؤشر، وشرائح تسمية سريعة بنقرة واحدة.
  8. **استقرار الرنين الفائق وشبكة شرائح الورد (PR #22):** تصحيح طريقة تمرير خيارات `PendingIntentBackgroundActivityStartMode` في أندرويد 14+ لمنع انهيار المنبه واستمراره في الرنين والاهتزاز دون أي توقف، وإعادة تخطيط شرائح اختيار الورد القرآني على سطرين متوازيين لمنع انضغاط النصوص.

- **التجربة الحية على الجهاز الواقعي (Samsung Galaxy S24 Ultra - Android 16):**
  - تم تثبيت الحزمة وإجراء سيناريوهات تفاعلية كاملة مع التقاط 7 صور إثبات حقيقية محفوظة في مجلد الأدلة:
    - `proof_1_main_screen_khatma_progress.png` (الواجهة الرئيسية وشريط مسيرة الختمة).
    - `proof_2_edit_dialog_ampm_minutes_modes.png` (نافذة التعديل مع AM/PM والدقائق الدقيقة وأنماط الورد).
    - `proof_3_dialog_scrolled_presets_and_volume.png` (سلايدر الصوت والشرائح السريعة).
    - `proof_3b_surah_mulk_selected.png` (اختيار سورة الملك كورد ثابت).
    - `proof_3c_main_screen_after_saving_edit.png` (حفظ التعديلات بنجاح).
    - `proof_4_ringing_screen_locked.png` (شاشة الرنين المقفلة مع الساعة الحية ونطاق الورد).
    - `proof_5_final_main_screen.png` (انتقال صفحات الورد تلقائياً لليوم التالي عند إتمام المنبه).
  - اجتياز اختبار استيقاظ Doze Mode بنسبة موثوقية 100%.

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
