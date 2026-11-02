# دليل المساهمة وإدارة الفروع (Contribution & Git Workflow Guide)
## مشروع: منبه القرآن الكريم (Quran Alarm)

نرحب بكافة المساهمين والمطورين في هذا المشروع المبارك. لضمان جودة الكود، وقدسية المصحف، وحماية الفرع الرئيسي، يلتزم كافة المساهمين ووكلاء الذكاء الاصطناعي بالقواعد التالية:

---

## 🛡️ سياسة حماية الفرع الرئيسي (Main Branch Protection)

* **حظر التعديل أو الدفع المباشر (No Direct Pushes to `main`):**
  - الفرع الرئيسي `main` محمي بشكل صارم.
  - **يُمنع منعاً باتاً تنفيذ أي `git push origin main` أو إضافة التزامات مباشرة عليه**.
  - محلياً، يقوم الـ Hook الموجود في `.githooks/pre-push` بإجهاض أي محاولة دفع مباشر لفرع `main`.
  - على GitHub، يتم تفعيل قواعد حماية الفروع (Branch Protection Rules) التي تمنع دمج أي كود دون مراجعة ونجاح فحص الـ CI.

---

## 🎯 قاعدة طلبات الدمج الصغيرة والمحددة (Small & Focused PRs)

* **القاعدة الذهبية: طلب دمج واحد لكل ميزة واحدة محددة:**
  - كل طلب دمج (Pull Request) يجب أن يفعل **شيئاً واحداً محدداً فقط** (Single Responsibility).
  - لا تخلط ميزات متعددة في طلب دمج واحد؛ مثلاً: برمجة `AlarmScheduler` تكون في طلب دمج مستقل، وبرمجة `AlarmReceiver` تكون في طلب دمج مستقل آخر.
* **الفوائد الهندسية لطلبات الدمج الصغيرة:**
  1. سهولة وسرعة مراجعة الكود وفهمه.
  2. عزل الأخطاء وسرعة تشخيص أي مشكلة برمجية عند ظهورها.
  3. إمكانية التراجع عن ميزة معينة (Rollback) دون التأثير على بقية أجزاء النظام.
  4. التحقق السريع من استيفاء شرط النجاح في [docs/ROADMAP.md](docs/ROADMAP.md).

---

## 🌿 اتفاقية تسمية الفروع (Branch Naming Conventions)

يجب إنشاء فرع جانبي مخصص لكل تعديل وفق الصيغ التالية:

| نوع التعديل | بادئة التسمية | مثال تطبيقي |
| :--- | :--- | :--- |
| ميزة جديدة تابعة للمراحل | `feat/p<رقم المرحلة>-<الوصف>` | `feat/p1-alarm-scheduler` |
| إصلاح خلل أو خطأ | `fix/<وصف الخلل>` | `fix/ear-debounce-threshold` |
| تحديث أو كتابة وثائق | `docs/<اسم الوثيقة>` | `docs/update-prd-scopes` |
| تحسين وإعادة هيكلة كود | `refactor/<اسم المكون>` | `refactor/coordinate-normalizer` |
| كتابة أو تعديل اختبارات | `test/<اسم الوحدة>` | `test/reading-fsm-unit-tests` |
| إعدادات البناء والـ CI | `ci/<اسم التعديل>` | `ci/add-lint-workflow` |

---

## 📝 اتفاقية تسمية الالتزامات (Conventional Commits)

نلتزم بنمط الالتزامات المعياري:
```text
<type>(<scope>): <short description in present tense>

# أمثلة:
feat(alarm): implement AlarmSchedulerImpl with setAlarmClock
test(vision): add unit tests for eye aspect ratio calculation
docs(adr): document ADR-002 on WebP mushaf pages selection
fix(audio): resolve audio focus race condition on phone call
```

---

## 🚀 دورة حياة المساهمة خطوة بخطوة (Workflow Steps)

1. **تحديث الفرع الرئيسي محلياً:**
   ```bash
   git checkout main
   git pull origin main
   ```
2. **إنشاء فرع جانبي للميزة:**
   ```bash
   git checkout -b feat/p1-alarm-scheduler
   ```
3. **كتابة الكود واختبارات الوحدة (TDD / Unit Tests):**
   - اكتب الكود الخاص بالميزة الواحدة المحددة.
   - اكتب اختبارات الوحدة الخاصة بها وتأكد من نجاحها محلياً:
     ```bash
     ./gradlew test
     ```
4. **التحقق من قائمة ميثاق المشروع ([AGENTS.md](AGENTS.md)):**
   - خلو البيان من إذن الإنترنت.
   - عدم تخزين أي إطار كاميرا على القرص.
   - عدم طلب صلاحيات خطرة.
5. **حفظ التعديلات والتسمية المعيارية:**
   ```bash
   git add .
   git commit -m "feat(alarm): implement AlarmScheduler using setAlarmClock"
   ```
6. **دفع الفرع الجانبي إلى GitHub:**
   ```bash
   git push -u origin feat/p1-alarm-scheduler
   ```
7. **فتح طلب الدمج (Pull Request):**
   - انتقل إلى مستودع GitHub وافتح Pull Request من فرعك الجانبي إلى فرع `main`.
   - املأ قالب [`.github/pull_request_template.md`](.github/pull_request_template.md).
   - تأكد من تحول فحص الـ CI إلى اللون الأخضر.
8. **الدمج والحذف:**
   - بعد المراجعة والاعتماد، يتم دمج الـ PR وحذف الفرع الجانبي للمحافظة على نظافة المستودع.
