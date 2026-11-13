package com.zoomai.studio

import android.graphics.ColorMatrix

/**
 * Editor effects: 24 bilingual presets built from [ColorMatrix] math, plus
 * user adjustments (intensity, brightness, contrast, saturation, warmth).
 * Everything runs locally on the bitmap — originals are never overwritten.
 */
data class EffectPreset(
    val nameFa: String,
    val nameEn: String,
    val categoryFa: String,
    val categoryEn: String,
    val build: () -> ColorMatrix
) {
    fun name(): String = I18n.s(nameFa, nameEn)
    fun category(): String = I18n.s(categoryFa, categoryEn)
}

object Effects {

    private fun raw(vararg v: Float) = ColorMatrix(v)
    private fun satM(s: Float) = ColorMatrix().apply { setSaturation(s) }
    private fun concat(a: ColorMatrix, b: ColorMatrix) = ColorMatrix().apply { setConcat(a, b) }
    private fun contrastM(c: Float, offset: Float) = raw(
        c, 0f, 0f, 0f, offset,
        0f, c, 0f, 0f, offset,
        0f, 0f, c, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
    private fun scaleM(r: Float, g: Float, b: Float, oR: Float = 0f, oG: Float = 0f, oB: Float = 0f) = raw(
        r, 0f, 0f, 0f, oR,
        0f, g, 0f, 0f, oG,
        0f, 0f, b, 0f, oB,
        0f, 0f, 0f, 1f, 0f
    )

    private fun preset(
        nameFa: String, nameEn: String, catFa: String, catEn: String,
        build: () -> ColorMatrix
    ) = EffectPreset(nameFa, nameEn, catFa, catEn, build)

    val presets: List<EffectPreset> = listOf(
        preset("اصلی", "Original", "پایه", "Basic") { ColorMatrix() },
        preset("زنده", "Vivid", "پایه", "Basic") { concat(contrastM(1.08f, -8f), satM(1.45f)) },
        preset("طبیعی", "Natural", "پایه", "Basic") { concat(contrastM(1.03f, -3f), satM(1.12f)) },
        preset("روشن و هوایی", "Bright & Airy", "پایه", "Basic") { concat(scaleM(1f, 1f, 1f, 20f, 20f, 20f), concat(contrastM(.9f, 0f), satM(1.05f))) },
        preset("درام", "Dramatic", "پایه", "Basic") { concat(contrastM(1.35f, -34f), satM(.9f)) },

        preset("سینمایی", "Cinematic Teal", "سینمایی", "Cinematic") {
            raw(1.06f, 0f, .04f, 0f, 0f, 0f, 1.0f, .05f, 0f, 2f, -.06f, .08f, 1.10f, 0f, 8f, 0f, 0f, 0f, 1f, 0f)
        },
        preset("فیلم", "Film", "سینمایی", "Cinematic") { concat(scaleM(1f, 1f, 1f, 12f, 12f, 12f), concat(contrastM(.92f, 0f), satM(.85f))) },
        preset("مات", "Matte", "سینمایی", "Cinematic") { concat(scaleM(1f, 1f, 1f, 18f, 18f, 18f), contrastM(.85f, 0f)) },
        preset("مود قهوه‌ای", "Moody Brown", "سینمایی", "Cinematic") {
            concat(contrastM(1.05f, -10f), raw(.40f, .72f, .17f, 0f, -6f, .35f, .66f, .16f, 0f, -6f, .26f, .50f, .12f, 0f, -4f, 0f, 0f, 0f, 1f, 0f))
        },

        preset("پرتره نرم", "Soft Portrait", "پرتره", "Portrait") { concat(scaleM(1.03f, 1f, .98f, 6f, 4f, 2f), concat(contrastM(.94f, 0f), satM(1.05f))) },
        preset("پرتره طلایی", "Golden Portrait", "پرتره", "Portrait") { concat(scaleM(1.12f, 1.02f, .88f, 8f, 3f, 0f), satM(1.10f)) },
        preset("پاستیل", "Pastel", "پرتره", "Portrait") { concat(scaleM(1f, 1f, 1f, 14f, 14f, 14f), concat(contrastM(.9f, 0f), satM(.7f))) },

        preset("سبز طبیعت", "Green Nature", "طبیعت", "Nature") { concat(scaleM(1f, 1.13f, 1f), satM(1.22f)) },
        preset("آسمان آبی", "Blue Sky", "طبیعت", "Nature") { concat(scaleM(1f, 1.03f, 1.16f), satM(1.16f)) },
        preset("ساعت طلایی", "Golden Hour", "طبیعت", "Nature") { scaleM(1.18f, 1.03f, .82f, 14f, 4f, 0f) },

        preset("شب آبی", "Night Blue", "شب", "Night") { scaleM(.88f, .99f, 1.20f, 0f, 2f, 8f) },
        preset("شب گرم شهری", "Warm City Night", "شب", "Night") { concat(contrastM(1.06f, -6f), scaleM(1.13f, 1.0f, .88f, 4f, 0f, 0f)) },
        preset("مهتاب", "Moonlight", "شب", "Night") { concat(scaleM(.94f, 1.0f, 1.13f, 4f, 4f, 6f), satM(.55f)) },

        preset("گرم", "Warm", "کلاسیک", "Classic") {
            raw(1.14f, 0f, 0f, 0f, 7f, 0f, 1.03f, 0f, 0f, 2f, 0f, 0f, .88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        },
        preset("سرد", "Cool", "کلاسیک", "Classic") {
            raw(.91f, 0f, 0f, 0f, 0f, 0f, 1.02f, 0f, 0f, 0f, 0f, 0f, 1.15f, 0f, 8f, 0f, 0f, 0f, 1f, 0f)
        },
        preset("قدیمی", "Vintage Sepia", "کلاسیک", "Classic") {
            raw(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        },

        preset("سیاه‌وسفید", "Black & White", "سیاه‌وسفید", "Mono") { satM(0f) },
        preset("سیاه‌وسفید پرکنتراست", "High-Contrast Mono", "سیاه‌وسفید", "Mono") { concat(contrastM(1.3f, -30f), satM(0f)) },
        preset("تک‌رنگ آبی", "Mono Blue", "سیاه‌وسفید", "Mono") { concat(scaleM(.88f, 1.0f, 1.16f), satM(0f)) }
    )

    val default: EffectPreset get() = presets.first()

    /** Blend a preset matrix with identity: intensity 0 = original, 1 = full preset. */
    private fun blendIdentity(source: ColorMatrix, intensity: Float): ColorMatrix {
        val t = intensity.coerceIn(0f, 1f)
        val src = source.array
        val identity = ColorMatrix().array
        val out = FloatArray(20) { i -> identity[i] + (src[i] - identity[i]) * t }
        return ColorMatrix(out)
    }

    /**
     * Full editor pipeline: preset (with intensity) → saturation → contrast/brightness → warmth.
     * brightness: -60..60 offset; contrast: 0.5..1.5; saturation: 0..2; warmth: -50..50.
     */
    fun compose(
        preset: EffectPreset,
        intensity: Float,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float
    ): ColorMatrix {
        val base = blendIdentity(preset.build(), intensity)
        val sat = satM(saturation)
        val cb = contrastM(contrast, brightness)
        val w = warmth / 50f
        val warm = scaleM(1f + w * .10f, 1f, 1f - w * .10f, w * 6f, 0f, -w * 6f)
        return concat(warm, concat(cb, concat(sat, base)))
    }
}
