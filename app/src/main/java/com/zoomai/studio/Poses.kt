package com.zoomai.studio

/**
 * Pose guide library — original vector stick-figure templates (no photos).
 *
 * Joint order for every figure (14 points, normalized 0..1 frame coordinates):
 * 0 head, 1 neck, 2 L-shoulder, 3 R-shoulder, 4 L-elbow, 5 R-elbow,
 * 6 L-wrist, 7 R-wrist, 8 L-hip, 9 R-hip, 10 L-knee, 11 R-knee,
 * 12 L-ankle, 13 R-ankle.
 *
 * A template has one figure (single person) or two/three figures
 * (couples, friends, family). Titles and categories are bilingual.
 */
data class PoseTemplate(
    val titleFa: String,
    val titleEn: String,
    val categoryFa: String,
    val categoryEn: String,
    val figures: List<List<Pair<Float, Float>>>
) {
    fun title(): String = I18n.s(titleFa, titleEn)
    fun category(): String = I18n.s(categoryFa, categoryEn)
}

object PoseCatalog {

    // ---- Base figures -------------------------------------------------------

    private val standing = listOf(
        .50f to .17f, .50f to .28f,
        .42f to .31f, .58f to .31f, .38f to .44f, .62f to .44f,
        .35f to .56f, .65f to .56f, .45f to .56f, .55f to .56f,
        .44f to .73f, .56f to .73f, .43f to .91f, .57f to .91f
    )

    private val sitting = listOf(
        .50f to .14f, .50f to .25f,
        .42f to .28f, .58f to .28f, .37f to .40f, .63f to .40f,
        .40f to .50f, .60f to .50f, .44f to .52f, .56f to .52f,
        .38f to .68f, .62f to .68f, .38f to .90f, .62f to .90f
    )

    private val crossLegged = listOf(
        .50f to .30f, .50f to .41f,
        .42f to .44f, .58f to .44f, .38f to .56f, .62f to .56f,
        .33f to .78f, .67f to .78f, .44f to .76f, .56f to .76f,
        .30f to .82f, .70f to .82f, .46f to .90f, .54f to .90f
    )

    // ---- Builders -----------------------------------------------------------

    private fun altered(
        base: List<Pair<Float, Float>>,
        vararg moves: Pair<Int, Pair<Float, Float>>
    ): List<Pair<Float, Float>> {
        val result = base.toMutableList()
        for ((i, pos) in moves) result[i] = pos
        return result
    }

    /** Move/scale a whole figure (partner, child, group member). */
    private fun moved(
        base: List<Pair<Float, Float>>,
        dx: Float = 0f,
        dy: Float = 0f,
        scale: Float = 1f
    ): List<Pair<Float, Float>> = base.map { (x, y) ->
        (0.5f + (x - 0.5f) * scale + dx) to (0.02f + (y - 0.02f) * scale + dy)
    }

    private fun pose(
        titleFa: String, titleEn: String,
        categoryFa: String, categoryEn: String,
        vararg figures: List<Pair<Float, Float>>
    ) = PoseTemplate(titleFa, titleEn, categoryFa, categoryEn, figures.toList())

    private val childStanding get() = moved(standing, dy = .30f, scale = .62f)
    private val childSitting get() = moved(sitting, dy = .34f, scale = .60f)

    // ---- Catalog ------------------------------------------------------------

    val all: List<PoseTemplate> = listOf(

        // فردی — Individual ---------------------------------------------------
        pose("ایستاده کلاسیک", "Classic Standing", "فردی", "Individual", standing),
        pose("دست روی کمر", "Hand on Waist", "فردی", "Individual",
            altered(standing, 4 to (.34f to .46f), 6 to (.44f to .55f))),
        pose("دست بالای سر", "Arm Overhead", "فردی", "Individual",
            altered(standing, 5 to (.64f to .20f), 7 to (.53f to .11f))),
        pose("دست‌ها ضربدری", "Arms Crossed", "فردی", "Individual",
            altered(standing, 4 to (.36f to .42f), 5 to (.64f to .42f), 6 to (.56f to .40f), 7 to (.44f to .40f))),
        pose("تکیه به دیوار", "Leaning on a Wall", "فردی", "Individual",
            altered(standing, 0 to (.46f to .16f), 1 to (.46f to .27f), 2 to (.38f to .30f), 3 to (.54f to .30f), 13 to (.47f to .88f))),
        pose("نگاه روی شانه", "Over-the-Shoulder Look", "فردی", "Individual",
            altered(standing, 0 to (.44f to .16f), 6 to (.37f to .54f), 7 to (.63f to .54f))),
        pose("دست‌ها پشت سر", "Hands Behind Head", "فردی", "Individual",
            altered(standing, 4 to (.33f to .30f), 5 to (.67f to .30f), 6 to (.44f to .14f), 7 to (.56f to .14f))),
        pose("دست تکان دادن", "Waving Hand", "فردی", "Individual",
            altered(standing, 5 to (.68f to .22f), 7 to (.72f to .08f))),
        pose("چهارزانو نشسته", "Sitting Cross-Legged", "فردی", "Individual", crossLegged),
        pose("زانو زده", "Kneeling", "فردی", "Individual",
            altered(standing, 8 to (.45f to .62f), 9 to (.55f to .62f), 10 to (.42f to .80f), 11 to (.58f to .80f), 12 to (.40f to .82f), 13 to (.60f to .82f))),
        pose("پرش", "Jumping", "فردی", "Individual",
            altered(standing, 0 to (.50f to .12f), 1 to (.50f to .23f), 6 to (.28f to .10f), 7 to (.72f to .10f), 10 to (.36f to .66f), 11 to (.64f to .66f), 12 to (.30f to .60f), 13 to (.70f to .60f))),
        pose("دست روی قلب", "Hand on Heart", "فردی", "Individual",
            altered(standing, 5 to (.60f to .42f), 7 to (.50f to .42f))),
        pose("قدم مطمئن", "Confident Stride", "فردی", "Individual",
            altered(standing, 10 to (.39f to .70f), 11 to (.63f to .74f), 12 to (.31f to .88f), 13 to (.75f to .85f), 6 to (.37f to .58f), 7 to (.63f to .50f))),

        // مردانه — Men ---------------------------------------------------------
        pose("دست در جیب", "Hands in Pockets", "مردانه", "Men",
            altered(standing, 6 to (.47f to .57f), 7 to (.53f to .57f))),
        pose("دست‌های گره‌کرده", "Arms Folded", "مردانه", "Men",
            altered(standing, 4 to (.33f to .44f), 5 to (.67f to .44f), 6 to (.47f to .38f), 7 to (.53f to .38f))),
        pose("کت روی شانه", "Jacket over Shoulder", "مردانه", "Men",
            altered(standing, 5 to (.66f to .28f), 7 to (.60f to .18f), 6 to (.47f to .57f))),
        pose("قدم زدن مردانه", "Men's Walk", "مردانه", "Men",
            altered(standing, 10 to (.40f to .70f), 11 to (.62f to .74f), 12 to (.32f to .86f), 13 to (.74f to .84f), 6 to (.36f to .58f), 7 to (.64f to .50f))),
        pose("نشستن روی پله", "Sitting on Steps", "مردانه", "Men",
            altered(sitting, 11 to (.60f to .58f), 13 to (.60f to .78f))),
        pose("پا روی دیوار", "Foot on the Wall", "مردانه", "Men",
            altered(standing, 11 to (.60f to .62f), 13 to (.66f to .62f))),

        // زنانه — Women --------------------------------------------------------
        pose("دست کنار صورت", "Hand by Face", "زنانه", "Women",
            altered(standing, 4 to (.36f to .38f), 6 to (.47f to .22f))),
        pose("دست در مو", "Hand in Hair", "زنانه", "Women",
            altered(standing, 4 to (.35f to .34f), 6 to (.46f to .13f))),
        pose("دست روی کمر (زنانه)", "Hand on Hip", "زنانه", "Women",
            altered(standing, 0 to (.45f to .16f), 5 to (.63f to .44f), 7 to (.55f to .55f))),
        pose("دو دست روی کمر", "Both Hands on Waist", "زنانه", "Women",
            altered(standing, 4 to (.34f to .44f), 6 to (.44f to .55f), 5 to (.66f to .44f), 7 to (.56f to .55f))),
        pose("نشستن ظریف", "Graceful Sitting", "زنانه", "Women",
            altered(sitting, 12 to (.52f to .88f), 13 to (.44f to .90f), 6 to (.42f to .62f))),
        pose("چرخش", "Twirl", "زنانه", "Women",
            altered(standing, 6 to (.24f to .48f), 7 to (.76f to .48f), 12 to (.47f to .90f), 13 to (.55f to .88f))),
        pose("تکیه به نرده", "Leaning on a Rail", "زنانه", "Women",
            altered(standing, 4 to (.36f to .40f), 5 to (.64f to .40f), 6 to (.38f to .48f), 7 to (.62f to .48f), 13 to (.48f to .89f))),
        pose("نشسته کنار پنجره", "Sitting by a Window", "زنانه", "Women",
            altered(sitting, 0 to (.44f to .13f), 5 to (.64f to .34f), 7 to (.50f to .20f))),

        // کودک — Kids ----------------------------------------------------------
        pose("کودک ایستاده", "Standing Kid", "کودک", "Kids", childStanding),
        pose("کودک دست بالا", "Kid, Hands Up", "کودک", "Kids",
            altered(childStanding, 6 to (.32f to .50f), 7 to (.68f to .50f))),
        pose("کودک در حال دویدن", "Running Kid", "کودک", "Kids",
            altered(childStanding, 10 to (.40f to .78f), 11 to (.62f to .80f), 12 to (.30f to .86f), 13 to (.72f to .84f), 6 to (.34f to .66f), 7 to (.66f to .62f))),
        pose("کودک نشسته", "Sitting Kid", "کودک", "Kids", childSitting),
        pose("کودک و توپ", "Kid Holding a Ball", "کودک", "Kids",
            altered(childStanding, 6 to (.44f to .66f), 7 to (.56f to .66f))),

        // خانواده — Family -------------------------------------------------------
        pose("خانواده سه‌نفره", "Family of Three", "خانواده", "Family",
            moved(standing, dx = -.25f), moved(standing, dx = .25f), moved(standing, dy = .34f, scale = .58f)),
        pose("مادر و کودک", "Mother and Child", "خانواده", "Family",
            moved(standing, dx = -.16f), moved(standing, dx = .22f, dy = .28f, scale = .60f)),
        pose("پدر و فرزند", "Father and Child", "خانواده", "Family",
            moved(standing, dx = -.18f), altered(moved(standing, dx = .20f, dy = .28f, scale = .60f), 6 to (.36f to .66f))),
        pose("خانواده نشسته", "Seated Family", "خانواده", "Family",
            moved(sitting, dx = -.22f), moved(sitting, dx = .22f), moved(sitting, dy = .36f, scale = .56f)),

        // دو نفره — Couples ------------------------------------------------------
        pose("دست در دست", "Holding Hands", "دو نفره", "Couples",
            moved(standing, dx = -.20f), moved(standing, dx = .20f)),
        pose("دست دور کمر", "Arm Around Waist", "دو نفره", "Couples",
            altered(moved(standing, dx = -.19f), 7 to (.52f to .58f)), moved(standing, dx = .19f)),
        pose("پشت به پشت", "Back to Back", "دو نفره", "Couples",
            altered(moved(standing, dx = -.16f), 6 to (.30f to .40f), 7 to (.40f to .38f)),
            altered(moved(standing, dx = .16f), 6 to (.60f to .38f), 7 to (.70f to .40f))),
        pose("آغوش", "Hugging", "دو نفره", "Couples",
            altered(moved(standing, dx = -.13f), 7 to (.55f to .40f)),
            altered(moved(standing, dx = .13f), 6 to (.45f to .40f))),
        pose("رقص دونفره", "Couple Dancing", "دو نفره", "Couples",
            altered(moved(standing, dx = -.17f), 7 to (.50f to .24f)),
            altered(moved(standing, dx = .17f), 6 to (.50f to .24f))),
        pose("نشستن دونفره", "Seated Couple", "دو نفره", "Couples",
            moved(sitting, dx = -.21f), moved(sitting, dx = .21f)),
        pose("نگاه به یکدیگر", "Looking at Each Other", "دو نفره", "Couples",
            altered(moved(standing, dx = -.20f), 0 to (.36f to .16f)),
            altered(moved(standing, dx = .20f), 0 to (.64f to .16f))),

        // دوستان — Friends -------------------------------------------------------
        pose("شانه‌به‌شانه", "Arms on Shoulders", "دوستان", "Friends",
            altered(moved(standing, dx = -.20f), 7 to (.62f to .34f)),
            altered(moved(standing, dx = .20f), 6 to (.38f to .34f))),
        pose("سه دوست", "Three Friends", "دوستان", "Friends",
            moved(standing, dx = -.27f),
            altered(standing, 6 to (.24f to .36f), 7 to (.76f to .36f)),
            moved(standing, dx = .27f)),
        pose("دوستان خندان", "Laughing Friends", "دوستان", "Friends",
            altered(moved(standing, dx = -.20f), 0 to (.26f to .14f), 7 to (.60f to .34f)),
            altered(moved(standing, dx = .20f), 0 to (.74f to .14f), 6 to (.40f to .34f))),
        pose("پشت به پشت (دوستان)", "Friends Back to Back", "دوستان", "Friends",
            moved(standing, dx = -.16f), moved(standing, dx = .16f)),
        pose("دست‌های بالا (دوستان)", "Friends, Hands Up", "دوستان", "Friends",
            altered(moved(standing, dx = -.19f), 7 to (.66f to .14f)),
            altered(moved(standing, dx = .19f), 6 to (.34f to .14f))),

        // جشن — Celebration ------------------------------------------------------
        pose("هورا، دو دست بالا", "Cheer, Both Arms Up", "جشن", "Celebration",
            altered(standing, 6 to (.30f to .08f), 7 to (.70f to .08f))),
        pose("پرش ستاره‌ای", "Star Jump", "جشن", "Celebration",
            altered(standing, 6 to (.22f to .14f), 7 to (.78f to .14f), 12 to (.32f to .84f), 13 to (.68f to .84f))),
        pose("رقص، یک دست بالا", "Dancing, One Arm Up", "جشن", "Celebration",
            altered(standing, 7 to (.70f to .10f), 6 to (.44f to .55f), 12 to (.40f to .89f), 13 to (.58f to .87f))),
        pose("دست زدن", "Clapping", "جشن", "Celebration",
            altered(standing, 6 to (.47f to .42f), 7 to (.53f to .42f))),
        pose("جشن نشسته", "Seated Celebration", "جشن", "Celebration",
            altered(sitting, 6 to (.44f to .46f), 7 to (.56f to .46f))),

        // خیابانی — Street --------------------------------------------------------
        pose("ژست راه رفتن", "Walking Pose", "خیابانی", "Street",
            altered(standing, 10 to (.39f to .70f), 11 to (.64f to .75f), 12 to (.31f to .87f), 13 to (.74f to .85f))),
        pose("عبور از خیابان", "Crossing the Street", "خیابانی", "Street",
            altered(standing, 10 to (.36f to .68f), 11 to (.66f to .72f), 12 to (.26f to .85f), 13 to (.78f to .82f), 6 to (.36f to .52f), 7 to (.64f to .58f))),
        pose("تکیه خیابانی", "Street Lean", "خیابانی", "Street",
            altered(standing, 0 to (.46f to .16f), 11 to (.60f to .64f), 13 to (.66f to .70f))),
        pose("راه رفتن با موبایل", "Walking with Phone", "خیابانی", "Street",
            altered(standing, 0 to (.50f to .19f), 6 to (.46f to .44f), 7 to (.54f to .44f), 10 to (.40f to .71f), 11 to (.61f to .74f), 12 to (.33f to .87f), 13 to (.72f to .85f))),
        pose("نشستن روی نیمکت", "Sitting on a Bench", "خیابانی", "Street", sitting),

        // طبیعت — Nature ----------------------------------------------------------
        pose("نگاه به دوردست", "Gazing Afar", "طبیعت", "Nature",
            altered(standing, 5 to (.67f to .40f), 7 to (.70f to .32f))),
        pose("دست‌ها باز به طبیعت", "Arms Open to Nature", "طبیعت", "Nature",
            altered(standing, 6 to (.20f to .40f), 7 to (.80f to .40f))),
        pose("نشستن روی سنگ", "Sitting on a Rock", "طبیعت", "Nature",
            altered(sitting, 10 to (.40f to .70f), 11 to (.60f to .70f), 12 to (.40f to .94f), 13 to (.60f to .94f))),
        pose("یوگا، ژست درخت", "Yoga Tree Pose", "طبیعت", "Nature",
            altered(standing, 6 to (.48f to .06f), 7 to (.52f to .06f), 13 to (.47f to .68f))),
        pose("مراقبه نشسته", "Seated Meditation", "طبیعت", "Nature",
            altered(crossLegged, 6 to (.33f to .76f), 7 to (.67f to .76f))),

        // آتلیه — Studio -----------------------------------------------------------
        pose("پرتره دست باز", "Open Arms Portrait", "آتلیه", "Studio",
            altered(standing, 4 to (.31f to .37f), 5 to (.69f to .37f), 6 to (.21f to .42f), 7 to (.79f to .42f))),
        pose("پرتره کلاسیک", "Classic Portrait", "آتلیه", "Studio",
            altered(standing, 6 to (.46f to .50f), 7 to (.54f to .50f))),
        pose("متفکر", "The Thinker", "آتلیه", "Studio",
            altered(standing, 5 to (.62f to .42f), 7 to (.50f to .24f), 6 to (.42f to .50f))),
        pose("نگاه مستقیم", "Direct Look", "آتلیه", "Studio",
            altered(standing, 0 to (.50f to .15f))),
        pose("ژست مد", "Fashion Pose", "آتلیه", "Studio",
            altered(standing, 7 to (.70f to .12f), 4 to (.34f to .44f), 6 to (.44f to .55f))),
        pose("نشسته روی چهارپایه", "Sitting on a Stool", "آتلیه", "Studio",
            altered(sitting, 8 to (.44f to .46f), 9 to (.56f to .46f), 10 to (.40f to .64f), 11 to (.60f to .64f), 12 to (.42f to .84f), 13 to (.58f to .84f))),

        // محصول — Product ------------------------------------------------------------
        pose("نگه داشتن محصول", "Holding a Product", "محصول", "Product",
            altered(standing, 6 to (.43f to .46f), 7 to (.57f to .46f))),
        pose("معرفی با یک دست", "Presenting a Product", "محصول", "Product",
            altered(standing, 5 to (.70f to .40f), 7 to (.82f to .40f))),
        pose("محصول بالای سر", "Product Overhead", "محصول", "Product",
            altered(standing, 6 to (.44f to .06f), 7 to (.56f to .06f))),
        pose("اشاره به محصول", "Pointing at a Product", "محصول", "Product",
            altered(standing, 5 to (.66f to .48f), 7 to (.78f to .56f)))
    )

    /** Distinct categories in catalog order (bilingual pairs). */
    val categories: List<Pair<String, String>>
        get() {
            val seen = LinkedHashMap<String, String>()
            for (p in all) seen.putIfAbsent(p.categoryFa, p.categoryEn)
            return seen.entries.map { it.key to it.value }
        }
}
