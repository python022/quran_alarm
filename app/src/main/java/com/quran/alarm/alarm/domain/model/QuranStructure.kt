package com.quran.alarm.alarm.domain.model

/**
 * Metadata representation of a Surah in the Madinah Mushaf (King Fahd Complex).
 */
data class SurahInfo(
    val number: Int,
    val nameArabic: String,
    val startPage: Int,
    val endPage: Int,
    val ayahCount: Int
)

/**
 * Metadata representation of a Hizb (1 to 60) in the Madinah Mushaf.
 */
data class HizbInfo(
    val number: Int,
    val startPage: Int,
    val endPage: Int
)

/**
 * Metadata representation of a Juz (1 to 30) in the Madinah Mushaf.
 */
data class JuzInfo(
    val number: Int,
    val startPage: Int,
    val endPage: Int
)

/**
 * Mode of selecting the Wird in the UI.
 */
enum class WirdSelectionMode(val titleArabic: String) {
    SURAHS("بالسور"),
    AHZAB("بالأحزاب"),
    AJZAA("بالأجزاء"),
    PAGES("بالصفحات")
}

/**
 * Forecast of Khatma completion based on current position and daily portion.
 */
data class KhatmaForecast(
    val currentStartPage: Int,
    val dailyPages: Int,
    val totalPagesRemaining: Int,
    val daysToComplete: Int,
    val progressPercentage: Float,
    val forecastSummary: String
)

/**
 * Comprehensive structural reference for the King Fahd Complex Madinah Mushaf (604 pages, 15 lines).
 * Complies strictly with the Quranic Sanctity Law in AGENTS.md.
 */
object QuranStructure {

    const val TOTAL_PAGES = 604
    const val TOTAL_SURAHS = 114
    const val TOTAL_JUZ = 30
    const val TOTAL_AHZAB = 60

    val SURAHS: List<SurahInfo> = listOf(
        SurahInfo(1, "الفاتحة", 1, 1, 7),
        SurahInfo(2, "البقرة", 2, 49, 286),
        SurahInfo(3, "آل عمران", 50, 76, 200),
        SurahInfo(4, "النساء", 77, 106, 176),
        SurahInfo(5, "المائدة", 106, 127, 120),
        SurahInfo(6, "الأنعام", 128, 150, 165),
        SurahInfo(7, "الأعراف", 151, 176, 206),
        SurahInfo(8, "الأنفال", 177, 186, 75),
        SurahInfo(9, "التوبة", 187, 207, 129),
        SurahInfo(10, "يونس", 208, 221, 109),
        SurahInfo(11, "هود", 221, 235, 123),
        SurahInfo(12, "يوسف", 235, 248, 111),
        SurahInfo(13, "الرعد", 249, 255, 43),
        SurahInfo(14, "إبراهيم", 255, 261, 52),
        SurahInfo(15, "الحجر", 262, 267, 99),
        SurahInfo(16, "النحل", 267, 281, 128),
        SurahInfo(17, "الإسراء", 282, 293, 111),
        SurahInfo(18, "الكهف", 293, 304, 110),
        SurahInfo(19, "مريم", 305, 312, 98),
        SurahInfo(20, "طه", 312, 321, 135),
        SurahInfo(21, "الأنبياء", 322, 331, 112),
        SurahInfo(22, "الحج", 332, 341, 78),
        SurahInfo(23, "المؤمنون", 342, 349, 118),
        SurahInfo(24, "النور", 350, 359, 64),
        SurahInfo(25, "الفرقان", 359, 366, 77),
        SurahInfo(26, "الشعراء", 367, 376, 227),
        SurahInfo(27, "النمل", 377, 385, 93),
        SurahInfo(28, "القصص", 385, 396, 88),
        SurahInfo(29, "العنكبوت", 396, 404, 69),
        SurahInfo(30, "الروم", 404, 410, 60),
        SurahInfo(31, "لقمان", 411, 414, 34),
        SurahInfo(32, "السجدة", 415, 417, 30),
        SurahInfo(33, "الأحزاب", 418, 427, 73),
        SurahInfo(34, "سبأ", 428, 434, 54),
        SurahInfo(35, "فاطر", 434, 440, 45),
        SurahInfo(36, "يس", 440, 445, 83),
        SurahInfo(37, "الصافات", 446, 452, 182),
        SurahInfo(38, "ص", 453, 458, 88),
        SurahInfo(39, "الزمر", 458, 467, 75),
        SurahInfo(40, "غافر", 467, 476, 85),
        SurahInfo(41, "فصلت", 477, 482, 54),
        SurahInfo(42, "الشورى", 483, 489, 53),
        SurahInfo(43, "الزخرف", 489, 495, 89),
        SurahInfo(44, "الدخان", 496, 498, 59),
        SurahInfo(45, "الجاثية", 499, 502, 37),
        SurahInfo(46, "الأحقاف", 502, 506, 35),
        SurahInfo(47, "محمد", 507, 510, 38),
        SurahInfo(48, "الفتح", 511, 515, 29),
        SurahInfo(49, "الحجرات", 515, 517, 18),
        SurahInfo(50, "ق", 518, 520, 45),
        SurahInfo(51, "الذاريات", 520, 523, 60),
        SurahInfo(52, "الطور", 523, 525, 49),
        SurahInfo(53, "النجم", 526, 528, 62),
        SurahInfo(54, "القمر", 528, 531, 55),
        SurahInfo(55, "الرحمن", 531, 534, 78),
        SurahInfo(56, "الواقعة", 534, 537, 96),
        SurahInfo(57, "الحديد", 537, 541, 29),
        SurahInfo(58, "المجادلة", 542, 545, 22),
        SurahInfo(59, "الحشر", 545, 548, 24),
        SurahInfo(60, "الممتحنة", 549, 551, 13),
        SurahInfo(61, "الصف", 551, 552, 14),
        SurahInfo(62, "الجمعة", 553, 554, 11),
        SurahInfo(63, "المنافقون", 554, 555, 11),
        SurahInfo(64, "التغابن", 556, 557, 18),
        SurahInfo(65, "الطلاق", 558, 559, 12),
        SurahInfo(66, "التحريم", 560, 561, 12),
        SurahInfo(67, "الملك", 562, 564, 30),
        SurahInfo(68, "القلم", 564, 566, 52),
        SurahInfo(69, "الحاقة", 566, 568, 52),
        SurahInfo(70, "المعارج", 568, 570, 44),
        SurahInfo(71, "نوح", 570, 571, 28),
        SurahInfo(72, "الجن", 572, 573, 28),
        SurahInfo(73, "المزمل", 574, 575, 20),
        SurahInfo(74, "المدثر", 575, 577, 56),
        SurahInfo(75, "القيامة", 577, 578, 40),
        SurahInfo(76, "الإنسان", 578, 580, 31),
        SurahInfo(77, "المرسلات", 580, 581, 50),
        SurahInfo(78, "النبأ", 582, 583, 40),
        SurahInfo(79, "النازعات", 583, 584, 46),
        SurahInfo(80, "عبس", 585, 586, 42),
        SurahInfo(81, "التكوير", 586, 586, 29),
        SurahInfo(82, "الانفطار", 587, 587, 19),
        SurahInfo(83, "المطففين", 587, 589, 36),
        SurahInfo(84, "الانشقاق", 589, 590, 25),
        SurahInfo(85, "البروج", 590, 590, 22),
        SurahInfo(86, "الطارق", 591, 591, 17),
        SurahInfo(87, "الأعلى", 591, 592, 19),
        SurahInfo(88, "الغاشية", 592, 593, 26),
        SurahInfo(89, "الفجر", 593, 594, 30),
        SurahInfo(90, "البلد", 594, 595, 20),
        SurahInfo(91, "الشمس", 595, 595, 15),
        SurahInfo(92, "الليل", 595, 596, 21),
        SurahInfo(93, "الضحى", 596, 596, 11),
        SurahInfo(94, "الشرح", 596, 597, 8),
        SurahInfo(95, "التين", 597, 597, 8),
        SurahInfo(96, "العلق", 597, 598, 19),
        SurahInfo(97, "القدر", 598, 598, 5),
        SurahInfo(98, "البينة", 598, 599, 8),
        SurahInfo(99, "الزلزلة", 599, 599, 8),
        SurahInfo(100, "العاديات", 599, 600, 11),
        SurahInfo(101, "القارعة", 600, 600, 11),
        SurahInfo(102, "التكاثر", 600, 600, 8),
        SurahInfo(103, "العصر", 601, 601, 3),
        SurahInfo(104, "الهمزة", 601, 601, 9),
        SurahInfo(105, "الفيل", 601, 601, 5),
        SurahInfo(106, "قريش", 602, 602, 4),
        SurahInfo(107, "الماعون", 602, 602, 7),
        SurahInfo(108, "الكوثر", 602, 602, 3),
        SurahInfo(109, "الكافرون", 603, 603, 6),
        SurahInfo(110, "النصر", 603, 603, 3),
        SurahInfo(111, "المسد", 603, 603, 5),
        SurahInfo(112, "الإخلاص", 604, 604, 4),
        SurahInfo(113, "الفلق", 604, 604, 5),
        SurahInfo(114, "الناس", 604, 604, 6)
    )

    val JUZ_START_PAGES = intArrayOf(
        1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
        201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
        402, 422, 442, 462, 482, 502, 522, 542, 562, 582
    )

    val HIZB_START_PAGES = intArrayOf(
        1, 11, 22, 32, 42, 51, 62, 72, 82, 92,
        102, 111, 121, 132, 142, 151, 162, 173, 182, 192,
        201, 211, 222, 231, 242, 252, 262, 272, 282, 292,
        302, 312, 322, 332, 342, 351, 362, 371, 382, 392,
        402, 413, 422, 431, 442, 451, 462, 472, 482, 491,
        502, 513, 522, 531, 542, 553, 562, 572, 582, 591
    )

    fun getSurahByNumber(number: Int): SurahInfo {
        require(number in 1..TOTAL_SURAHS) { "Surah must be between 1 and $TOTAL_SURAHS" }
        return SURAHS[number - 1]
    }

    fun getStartPageForJuz(juzNumber: Int): Int {
        require(juzNumber in 1..TOTAL_JUZ) { "Juz must be between 1 and $TOTAL_JUZ" }
        return JUZ_START_PAGES[juzNumber - 1]
    }

    fun getEndPageForJuz(juzNumber: Int): Int {
        require(juzNumber in 1..TOTAL_JUZ) { "Juz must be between 1 and $TOTAL_JUZ" }
        return if (juzNumber == TOTAL_JUZ) TOTAL_PAGES else JUZ_START_PAGES[juzNumber] - 1
    }

    fun getStartPageForHizb(hizbNumber: Int): Int {
        require(hizbNumber in 1..TOTAL_AHZAB) { "Hizb must be between 1 and $TOTAL_AHZAB" }
        return HIZB_START_PAGES[hizbNumber - 1]
    }

    fun getEndPageForHizb(hizbNumber: Int): Int {
        require(hizbNumber in 1..TOTAL_AHZAB) { "Hizb must be between 1 and $TOTAL_AHZAB" }
        return if (hizbNumber == TOTAL_AHZAB) TOTAL_PAGES else HIZB_START_PAGES[hizbNumber] - 1
    }

    fun getSurahAtPage(page: Int): SurahInfo {
        require(page in 1..TOTAL_PAGES) { "Page must be between 1 and $TOTAL_PAGES" }
        // Return the primary/first surah starting on or spanning across this page
        return SURAHS.firstOrNull { it.startPage == page }
            ?: SURAHS.lastOrNull { it.startPage < page }
            ?: SURAHS.first()
    }

    fun getSurahsAtPage(page: Int): List<SurahInfo> {
        require(page in 1..TOTAL_PAGES) { "Page must be between 1 and $TOTAL_PAGES" }
        val startingHere = SURAHS.filter { it.startPage == page }
        if (startingHere.isNotEmpty()) return startingHere
        val spanning = SURAHS.lastOrNull { it.startPage < page }
        return if (spanning != null) listOf(spanning) else listOf(SURAHS.first())
    }

    fun getJuzAtPage(page: Int): Int {
        require(page in 1..TOTAL_PAGES) { "Page must be between 1 and $TOTAL_PAGES" }
        for (i in JUZ_START_PAGES.indices.reversed()) {
            if (page >= JUZ_START_PAGES[i]) {
                return i + 1
            }
        }
        return 1
    }

    fun getHizbAtPage(page: Int): Int {
        require(page in 1..TOTAL_PAGES) { "Page must be between 1 and $TOTAL_PAGES" }
        for (i in HIZB_START_PAGES.indices.reversed()) {
            if (page >= HIZB_START_PAGES[i]) {
                return i + 1
            }
        }
        return 1
    }

    /**
     * Calculates Khatma forecast based on current page and daily reading amount.
     */
    fun calculateKhatmaForecast(startPage: Int, dailyPages: Int): KhatmaForecast {
        val safeStart = startPage.coerceIn(1, TOTAL_PAGES)
        val safeDaily = dailyPages.coerceAtLeast(1)
        val remaining = TOTAL_PAGES - safeStart + 1
        val days = Math.ceil(remaining.toDouble() / safeDaily.toDouble()).toInt()
        val percentage = ((safeStart - 1).toFloat() / TOTAL_PAGES.toFloat()) * 100f

        val summary = if (remaining <= safeDaily) {
            "اليوم موعد الختمة المباركة بإذن الله!"
        } else {
            "الختمة المتوقعة خلال $days يوم ($remaining صفحة متبقية)"
        }

        return KhatmaForecast(
            currentStartPage = safeStart,
            dailyPages = safeDaily,
            totalPagesRemaining = remaining,
            daysToComplete = days,
            progressPercentage = percentage,
            forecastSummary = summary
        )
    }
}
