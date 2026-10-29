package com.personal.aftermathtv

import android.graphics.Color

/** Colour palettes sampled from real satellite-box guides. */
class GuideTheme(
    val bgTop: Int, val bgBottom: Int, val infoPanel: Int,
    val pill: Int,
    val chanTop: Int, val chanBottom: Int,
    val headTop: Int, val headBottom: Int, val dateTop: Int, val dateBottom: Int,
    val cellTop: Int, val cellBottom: Int, val cellText: Int, val cellBold: Boolean,
    val cellBorder: Int, val cellInset: Float, val gridBg: Int,
    val selTop: Int, val selBottom: Int, val selText: Int,
    val arrow: Int,
    val stripBg: Int, val stripText: Int
) {
    companion object {
        /** Silver cells with blue text, green selection (the 2019-era guide). */
        val GRAY = GuideTheme(
            bgTop = Color.rgb(22, 116, 188), bgBottom = Color.rgb(12, 66, 140), infoPanel = Color.rgb(16, 58, 128),
            pill = Color.rgb(60, 152, 66),
            chanTop = Color.rgb(126, 196, 240), chanBottom = Color.rgb(92, 168, 226),
            headTop = Color.rgb(34, 126, 204), headBottom = Color.rgb(22, 100, 176),
            dateTop = Color.rgb(40, 120, 196), dateBottom = Color.rgb(26, 96, 170),
            cellTop = Color.rgb(232, 234, 238), cellBottom = Color.rgb(198, 202, 210),
            cellText = Color.rgb(44, 92, 176), cellBold = false,
            cellBorder = Color.rgb(42, 66, 162), cellInset = 0f, gridBg = Color.rgb(42, 66, 162),
            selTop = Color.rgb(58, 164, 70), selBottom = Color.rgb(36, 134, 52), selText = Color.rgb(212, 255, 208),
            arrow = Color.rgb(96, 100, 112),
            stripBg = Color.argb(240, 64, 150, 216), stripText = Color.rgb(10, 44, 110)
        )

        /** Lavender cells with navy text, gold selection (the earlier HD guide). */
        val BLUE = GuideTheme(
            bgTop = Color.rgb(10, 38, 122), bgBottom = Color.rgb(8, 28, 100), infoPanel = 0,
            pill = Color.rgb(196, 214, 78),
            chanTop = Color.rgb(104, 196, 252), chanBottom = Color.rgb(62, 160, 238),
            headTop = Color.rgb(56, 112, 214), headBottom = Color.rgb(38, 88, 196),
            dateTop = Color.rgb(48, 104, 214), dateBottom = Color.rgb(32, 80, 190),
            cellTop = Color.rgb(124, 170, 236), cellBottom = Color.rgb(150, 194, 244),
            cellText = Color.rgb(14, 28, 100), cellBold = true,
            cellBorder = 0, cellInset = 1.5f, gridBg = Color.rgb(22, 52, 150),
            selTop = Color.rgb(216, 198, 72), selBottom = Color.rgb(192, 174, 48), selText = Color.WHITE,
            arrow = Color.rgb(14, 28, 100),
            stripBg = Color.argb(240, 64, 150, 216), stripText = Color.rgb(10, 44, 110)
        )

        fun current(): GuideTheme = if (Config.GUIDE_THEME.equals("blue", ignoreCase = true)) BLUE else GRAY
    }
}
