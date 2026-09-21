package com.tepmex.duoshaoqian.data

import androidx.compose.ui.graphics.Color

data class Banknote(
    val yuan: Int,
    val chineseValue: String,
    val background: Color,
    val ink: Color,
    val accent: Color,
)

object RmbWallet {
    val notes: List<Banknote> = listOf(
        Banknote(1, "壹圆", Color(0xFFB7C45A), Color(0xFF3F4A18), Color(0xFF6E7A2E)),
        Banknote(5, "伍圆", Color(0xFF9B6BB3), Color(0xFF3A1D4A), Color(0xFF6A3D82)),
        Banknote(10, "拾圆", Color(0xFF2F7CC4), Color(0xFF0D2F55), Color(0xFF1B568C)),
        Banknote(20, "贰拾圆", Color(0xFFC4843C), Color(0xFF4A2A10), Color(0xFF8A5520)),
        Banknote(50, "伍拾圆", Color(0xFF2F8A68), Color(0xFF0E3326), Color(0xFF1F5C45)),
        Banknote(100, "壹佰圆", Color(0xFFC4453A), Color(0xFF4A1210), Color(0xFF8A2A24)),
    )
}
