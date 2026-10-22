# PHASE 4.3 — CHARACTER PHYSICAL SCALE & COMPATIBILITY BALANCING REPORT
## تقرير تنفيذ نظام معايرة الحجم المرئي والأبعاد الفيزيائية وتوافق المقاتلين

**التاريخ (Date):** 21 أكتوبر 2026  
**المرحلة:** PHASE 4.3 — Character Physical Scale & Compatibility Balancing  
**طبيعة المرحلة:** توسيع وتطوير محرك موازنة الشخصيات ليشمل الأبعاد الفيزيائية (Physical Dimensions)، المقياس المرئي (Visual Scale)، مساحات الدفع والتصادم (Pushbox & Collision Bounds)، والتوافق الحركي (Movement Physics) مع الذكاء الاصطناعي ومساحة العمل الآمنة.  
**الحالة العامة للمشروع:** **145/145 UNIT TESTS PASS (100%) | ZERO REGRESSIONS | IMMUTABLE VAULT VERIFIED | COMPILE PASS**

---

## 1. الملخص التنفيذي (Executive Summary)

تم بنجاح إتمام وتفعيل مرحلة **PHASE 4.3** لمعالجة وتجاوز إحدى أبرز المشاكل التقنية والبصرية الشائعة في بيئة MUGEN/IKEMEN-GO على الأجهزة المحمولة:
1. **تشوه وتفاوت الأحجام (Scale Inconsistency):** تفاوت المقاييس بين المقاتلين المستوردين من مصادر مختلفة (مثل حزم Street Fighter أو ألعاب Capcom وSNK أو مقاتلي الدقة العالية HD)، مما كان يتسبب في ظهور بعض المقاتلين بأحجام عملاقة غير متناسقة مقارنة بخصومهم في ساحة القتال نفسها.
2. **محرك تحليل محلي خفيف ومستقل (`CharacterScaleAnalyzer`):** تم بناء محرك مستقل نقي ضمن حزمة `com.example.engine.scale` يستخرج ويحلل المقاييس البصرية (`xscale`, `yscale`, `aspectRatio`) والأبعاد الفيزيائية (`height`, `ground.front`, `ground.back`, `air.front`, `air.back`, `head.pos`, `mid.pos`) ومؤشرات الحركة (`walk.fwd`, `run.fwd`, `jump.neu`, `yaccel`) ومعاملات الدقة المحلية (`localcoord`) من ملفات `.def` و`.cns` و`.air`.
3. **تصنيف الفئات التنافسية المحافظ على الهوية (`ScaleArchetype`):** تصنيف المقاتلين إلى فئات محددة (`TINY_SMALL`, `STANDARD`, `TALL_LARGE`, `GIANT_BOSS`, `SPECIAL_UNCLASSIFIED`) تمنع التسطيح الموحد وتحافظ على هيبة الزعماء والعمالقة وتفرد المقاتلين الأقزام دون إجبار الجميع على طول واحد.
4. **كشف الشذوذ والمعايرة التناسبية (`Proportional Calibration`):**
   - إصلاح تشوه نسبة الأبعاد (Harmonization) لضمان تطابق مقياس العرض والارتفاع (`xscale == yscale`).
   - معايرة تدريجية للأحجام المفرطة باستخدام التخميد غير الخطي.
   - مواءمة تلقائية متناسبة لمربعات الدفع والارتفاع الفيزيائي تمنع حدوث "الضربات الوهمية" (Phantom Hitboxes) خارج أبعاد المجسم.
5. **تكامل متقدم مع Google Gemini (`gemini-3.8-flash`):**
   - توسيع مخطط JSON المنظم ليتضمن اقتراحات الحجم والمقياس وشروحات الأبعاد.
   - تطبيق مبدأ "انعدام الثقة" (Zero Trust) والتقييد الصارم للقيم المقترحة ضمن النطاق `[0.35, 2.50]`.
   - توفير بديل محلي فوري وآمن (Fallback) عند انقطاع الاتصال أو غياب المفتاح.
6. **واجهة مستخدم تفاعلية متكاملة (`RosterBalancingDialog`):**
   - إضافة قسم وتبويب مخصص للأبعاد الفيزيائية والمقياس.
   - عرض فئات المقاتلين، وشارات المقارنة قبل/بعد للمقياس ومربع الدفع والارتفاع.
   - تمكين التحكم اليدوي والاختيار الفردي للمعايرة الفيزيائية.
7. **أمان تام لمساحة العمل والتراجع الذري (`CharacterWorkspaceManager`):**
   - انعدام تام لأي تعديل على القرص أثناء التحليل (In-Memory Analysis Only / SHA-256 Invariant).
   - تطبيق التعديلات عبر `saveCompleteCharacterEdits` في معاملة ذرية متزامنة بين `.cns` و`.def` مع أخذ لقطة فورية في الخزينة المحصنة (Pristine Vault) وإتاحة التراجع الفوري بنقرة واحدة.
8. **حزمة اختبارات شاملة:** نجاح كافة الاختبارات العشرة المتخصصة الجديدة في `CharacterScaleBalancingTest` وارتفاع إجمالي اختبارات المشروع إلى **145 اختبار وحدة ناجحاً بنسبة 100%**.

---

## 2. الهندسة البرمجية ونماذج البيانات (Architecture & Models)

تم تنظيم المنظومة المعيارية ضمن الحزم التالية:

### أ. ملف المحرك: `app/src/main/java/com/example/engine/scale/CharacterScaleAnalyzer.kt`

#### 1. النماذج والتصنيفات (Enums & Config):
- **`ScaleArchetype`:**
  - `TINY_SMALL` (0.50 - 0.85): للمقاتلين الصغار والأقزام (مثل Servbot، Chopper).
  - `STANDARD` (0.85 - 1.15): للمقاتلين البشريين القياسيين (مثل Ryu، Ken).
  - `TALL_LARGE` (1.15 - 1.40): للمقاتلين طوال القامة والضخام (مثل Sagat، Zangief).
  - `GIANT_BOSS` (1.40 - 2.50): للزعماء العمالقة (مثل Apocalypse، Onslaught، Sentinel).
  - `SPECIAL_UNCLASSIFIED` (0.35 - 2.50): للكائنات الخاصة غير المصنفة.
- **`ScaleAnomalyType`:**
  - `ASPECT_RATIO_DISTORTION`: تفاوت غير مبرر بين X وY يؤدي إلى انضغاط أو تمدد الرسم.
  - `OVERSIZED_SPRITE`: مقياس مرئي مفرط يتجاوز النطاق التنافسي المعتاد.
  - `UNDERSIZED_SPRITE`: حجم ضئيل للغاية يعيق دقة الاشتباك وتحديد الضربات.
  - `PUSHBOX_SCALE_MISMATCH`: تباين بين مساحة الدفع/الارتفاع الفيزيائي ومقياس الرسم.
  - `MISSING_OR_MISMATCHED_LOCALCOORD`: تعارض بين الدقة المحلية المصرحة وأبعاد الرسوم.
  - `SPEED_SCALE_IMBALANCE`: عدم تناسق سرعة الحركة الفيزيائية مع حجم ومقياس الجسم.
- **`ScaleBalancingConfig`:**
  - `standardScaleMin = 0.85`، `standardScaleMax = 1.15`
  - `absoluteFloorScale = 0.35`، `absoluteCapScale = 2.50`
  - `maxAllowedAspectRatioDeviation = 0.05`
  - `bossScaleCap = 1.65`
  - `dampingDivisor = 2.2`

#### 2. نماذج المقاييس والأبعاد (Data Models):
- **`PhysicalDimensions`:** `height`, `groundFront`, `groundBack`, `airFront`, `airBack`, `headPosX`, `headPosY`, `midPosX`, `midPosY`, `standSizebox`, `airSizebox`.
- **`ScaleMovementMetrics`:** `walkFwd`, `walkBack`, `runFwdX`, `runFwdY`, `jumpNeuY`, `yAccel`.
- **`VisualScaleMetrics`:** `xScale`, `yScale`, `aspectRatio`, `declaredLocalcoord`, `localcoordWidth`, `localcoordHeight`.
- **`CharacterScaleProfile`:** ملف التعريف الفيزيائي المتكامل للشخصية.
- **`CharacterScaleProposal`:** مقترح المعايرة الفيزيائية والمبررات والشروحات باللغة العربية.

---

## 3. منطق الكشف والمعايرة التناسبية المحافظة على الهوية (Proportional Balancing Engine)

### أ. التحليل والاستخراج المتكامل:
- قراءة قسم `[size]` في ملف `.cns` لاستخراج `xscale` و`yscale`، وحساب نسبة التناسب (`aspectRatio = xScale / yScale`).
- استخراج دقة الشاشة المحلية `localcoord` من ملف `.def` ومقارنتها بدقة MUGEN القياسية (320x240) أو الدقة العالية (640x480 و1280x720).
- استخراج مواضع الرأس والوسط ومربعات الحجم وسرعات الحركة.

### ب. تصنيف الفئة الرياضية (Archetype Classification):
- فحص الكلمات المفتاحية في معرف الشخصية واسمها (Boss, Apocalypse, Onslaught, Giant, Servbot, etc.).
- مطابقة الارتفاع الفيزيائي والمقياس الراهن لتحديد فئة المقاتل تلقائياً بدقة.

### ج. معادلات التخميد والمعايرة التناسبية:
1. **معالجة تشوه الأبعاد (Aspect Ratio Repair):**
   - في حال وجود تفاوت `|xScale - yScale| > 0.05`، يتم توحيد المقياس التوافقي:
   $$\text{targetScale} = \frac{\text{xScale} + \text{yScale}}{2}$$
2. **التخميد غير الخطي للأحجام المفرطة (Non-linear Damping):**
   - بالنسبة للمقاتل القياسي ذي الحجم المفرط، يتم تقريبه تدريجياً نحو الهدف المتوازن دون إسقاط حاد:
   $$\text{targetScale} = 1.0 + \frac{\text{avgScale} - 1.0}{\text{dampingDivisor}}$$
3. **الحفاظ على هيبة الزعماء (Boss Tolerance):**
   - يُسمح للزعماء بمقياس يصل إلى $1.65$ دون إجبارهم على مقياس المقاتلين العاديين ($1.0$).
4. **المواءمة التناسبية لمربعات الدفع (Pushbox Synchronization):**
   - لحماية طريقة اللعب من الضربات الوهمية أو اختراق المجسمات، يتم تعديل الارتفاع ومساحة الدفع طردياً مع المقياس الجديد:
   $$k = \frac{\text{proposedScale}}{\text{originalYScale}}$$
   $$\text{proposedHeight} = \text{round}(\text{originalHeight} \times k)$$
   $$\text{proposedGroundFront} = \text{round}(\text{originalGroundFront} \times k)$$
   $$\text{proposedGroundBack} = \text{round}(\text{originalGroundBack} \times k)$$

---

## 4. التكامل مع الذكاء الاصطناعي (Gemini AI Integration)

تم توسيع `GeminiApiClient` و`GeminiRosterBalancer`:
1. **تمرير بيانات الحجم والأبعاد:** يتم تضمين `xscale`, `yscale`, `height`, `groundFront`, `groundBack`, `localcoord` ضمن مصفوفة المقاتلين المرسلة للنموذج.
2. **توسيع المخطط الهيكلي (Structured Response Schema):**
   - إضافة الحقول الاختيارية: `proposedXScale` (NUMBER), `proposedYScale` (NUMBER), `scaleRationale` (STRING).
3. **التقييد الصارم (Zero-Trust Clamping):**
   - تقييد المقياس المقترح ضمن الحدود الصلبة $[0.35, 2.50]$.
   - فرض التناسب المتماثل بين X وY لمنع التشويه.
   - مواءمة الارتفاع الفيزيائي ومساحة الدفع تلقائياً استناداً إلى المقياس المعتمد من الذكاء الاصطناعي.
4. **الرجوع الآمن (Graceful Fallback):**
   - في حال حدوث أي خطأ شبكي أو نفاد الحصة، يتم تفعيل مقترحات `CharacterScaleAnalyzer` المحلية فوراً مع إشعار المستخدم الشفاف.

---

## 5. تجربة المستخدم في واجهة التطبيق (UI Integration)

تم تحديث واجهة `RosterBalancingDialog.kt`:
1. **تبويبات التصفية السريعة (Filter Tabs):**
   - تبويب "الكل" (All).
   - تبويب "السمات القتالية" (Combat Stats: Life / Attack / Defence).
   - تبويب "المقياس والأبعاد" (Physical Scale & Pushbox).
2. **شارات المقارنة البصرية (Before & After Badges):**
   - عرض المقياس الأصلي والمقترح (`1.5x → 1.227x`).
   - عرض الارتفاع الأصلي والمقترح (`80px → 65px`).
   - عرض مساحة الدفع الأرضية (`[20, 18] → [16, 15]`).
   - شارات التنبيه لشذوذ الأبعاد وتصنيف الفئة (`مقاتل قياسي`, `زعيم عملاق`).
3. **التحكم المستقل بالمعايرة:**
   - توفير خانة اختيار مستقلة لمعايرة الحجم لكل شخصية تتيح للمستخدم تطبيق تعديل الخصائص القتالية فقط أو تطبيق معايرة الأبعاد معها.
4. **التطبيق الذري المزدوج (Atomic Application):**
   - حفظ التعديلات على ملفات `.cns` و`.def` عبر `CharacterWorkspaceManager.saveCompleteCharacterEdits` في معاملة ذرية متزامنة واحدة تضمن حماية الخزينة وإمكانية التراجع الكامل.

---

## 6. مصفوفة التحقق واختبارات الوحدة (Test Execution & Verification Matrix)

تم إنشاء حزمة اختبارات الوحدة المتخصصة `CharacterScaleBalancingTest.kt` التي تغطي 10 سيناريوهات صارمة:

| رقم الاختبار | اسم الاختبار | الوصف والنتيجة | الحالة |
| :---: | :--- | :--- | :---: |
| **01** | `test1_StandardScaleCharacter` | التحقق من أن الشخصية القياسية لا تولد أي مقترحات تعديل وتصنف بدقة كـ STANDARD. | **PASS** |
| **02** | `test2_OversizedCharacterDampingAndProportionalPushbox` | التحقق من كشف الحجم المفرط (1.5x) وتخميده نحو 1.0 مع تعديل الارتفاع الفيزيائي طردياً. | **PASS** |
| **03** | `test3_AspectRatioDistortionHarmonization` | التحقق من كشف تشوه الأبعاد (x=1.0, y=1.35) وتوحيد مقياسي العرض والارتفاع. | **PASS** |
| **04** | `test4_BossAndTinyArchetypeTolerance` | التحقق من تصنيف الزعيم (Apocalypse) والمقاتل القزم (Servbot) والحفاظ على خصوصية حجمهما دون تسطيح. | **PASS** |
| **05** | `test5_ModernAndLegacySizePropertiesParsing` | التحقق من القراءة السليمة لكافة خصائص [size] و[velocity] و[movement] الحديثة والقديمة. | **PASS** |
| **06** | `test6_MissingPropertiesFallbackSafety` | التحقق من التعامل الآمن مع ملفات CNS التي تخلو من خصائص الحجم والاعتماد على 1.0 دون أخطاء. | **PASS** |
| **07** | `test7_LocalcoordResolutionAndMismatchDetection` | التحقق من قراءة localcoord من ملف .def وكشف التناقض عند وجود مقياس مفرط مع دقة عالية. | **PASS** |
| **08** | `test8_PushboxScaleMismatchDetection` | التحقق من كشف التناقض بين مساحة الدفع/الارتفاع الفيزيائي والمقياس المرئي. | **PASS** |
| **09** | `test9_WorkspaceIsolationAndImmutabilityDuringAnalysis` | التحقق الصارم من انعدام أي كتابة على القرص أثناء التحليل عبر مطابقة SHA-256 للملفات الأصلية. | **PASS** |
| **10** | `test10_AtomicSaveReloadAndRollbackViaWorkspaceManager` | التحقق من الحفظ الذري لخصائص الحجم في مساحة العمل، وإعادة التحميل، والتراجع التام للنسخة الأصلية. | **PASS** |

### نتيجة تنفيذ الاختبارات الإجمالية للمشروع:
- **إجمالي اختبارات الوحدة:** **145 اختباراً ناجحاً بنسبة 100% (0 failures, 0 errors, 0 skipped)**
- **حزم الاختبارات:** 16 حزمة اختبارات متكاملة تغطي كافة جوانب التطبيق ومحرك MUGEN.

---

## 7. حدود التحقق والتوصيات التشغيلية (Verification Boundaries)

- **ما تم التحقق منه آلياً بنسبة 100% (Automated Verification):**
  - استخراج وتحليل خصائص المقاييس والأبعاد الفيزيائية ومربعات الدفع ومؤشرات الحركة من ملفات `.cns` و`.def` و`.air`.
  - خوارزميات تصنيف الفئات واكتشاف الشذوذ والمعايرة التناسبية المحافظة على الهوية.
  - أمان مساحة العمل: عدم تعديل الملفات الأصلية، إنشاء اللقطات في الخزينة المحصنة، التراجع الذري، ومطابقة بصمات SHA-256.
  - تكامل Gemini وتطبيق قيود انعدام الثقة (Zero Trust Bounds) والرجوع التلقائي للبديل المحلي.
  - بناء وتجميع التطبيق بنجاح دون أي أخطاء ترجمة.
- **ما يتطلب الفحص على الجهاز الفعلي (Physical Device Runtime Testing):**
  - المعاينة البصرية داخل المباراة في محرك IKEMEN-GO للتأكد من تباين الارتفاع بين شخصيات ذات أصول مختلفة في ساحات قتال متعددة الدقة.
  - الإحساس الحركي وتفاعل ضربات المقاتل المصغر أو المكبر مع الأزرار الافتراضية وشاشات اللمس.
