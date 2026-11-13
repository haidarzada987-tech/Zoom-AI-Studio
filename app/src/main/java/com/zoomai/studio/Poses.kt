package com.zoomai.studio

import android.content.Context
import org.json.JSONArray

/**
 * Pose library v2 (v0.4.0) — real photos bundled in `assets/poses/`, described
 * by `assets/poses/manifest.json`. The library is fully data-driven: photos,
 * bilingual titles, categories and per-pose tips all come from the manifest,
 * so the set can grow (or be downloaded and cached later) without code changes.
 *
 * [PoseTemplate] is kept only as the joint-guide type that [PoseOverlayView]
 * still knows how to draw; the old hand-drawn vector catalog was removed in
 * v0.4.0 — the user asked for real photos instead of stick figures.
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

/** One real pose photo from the bundled manifest. */
data class PosePhoto(
    val file: String,
    val titleFa: String,
    val titleEn: String,
    val category: String,
    val tipFa: String,
    val tipEn: String
) {
    fun title(): String = I18n.s(titleFa, titleEn)
    fun tip(): String = I18n.s(tipFa, tipEn)
}

object PoseLibrary {

    /**
     * Filter chips in display order: key + FA + EN labels. The "all" chip is
     * added by the UI; these are the real manifest categories (v2 set).
     * Family/kids return in Phase 4 with the full library.
     */
    val categoryLabels: List<Triple<String, String, String>> = listOf(
        Triple("women", "زنانه", "Women"),
        Triple("men", "مردانه", "Men"),
        Triple("couple", "دو نفره", "Couple"),
        Triple("fashion", "فشن", "Fashion")
    )

    fun categoryLabel(key: String): String {
        val found = categoryLabels.firstOrNull { it.first == key } ?: return key
        return I18n.s(found.second, found.third)
    }

    /** Load every pose entry from the manifest; a broken manifest yields an empty library, never a crash. */
    fun load(context: Context): List<PosePhoto> {
        val list = mutableListOf<PosePhoto>()
        try {
            val text = context.assets.open("poses/manifest.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PosePhoto(
                        file = o.getString("file"),
                        titleFa = o.optString("title_fa"),
                        titleEn = o.optString("title_en"),
                        category = o.optString("category"),
                        tipFa = o.optString("tip_fa"),
                        tipEn = o.optString("tip_en")
                    )
                )
            }
        } catch (e: Exception) {
            // Library stays empty if the manifest is missing; the camera still works.
        }
        return list
    }
}
