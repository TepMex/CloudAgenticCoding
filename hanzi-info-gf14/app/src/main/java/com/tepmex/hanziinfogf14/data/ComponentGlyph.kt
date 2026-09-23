package com.tepmex.hanziinfogf14.data

object ComponentGlyph {
    /** Single-code-point components stay as glyphs. IDS placeholders collapse to a mark. */
    fun display(component: String): String {
        if (component.isEmpty()) return MARK
        return if (component.codePointCount(0, component.length) == 1) component else MARK
    }

    const val MARK = "◌"
}
