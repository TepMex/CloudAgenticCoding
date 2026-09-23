package com.tepmex.hanziinfogf14.data

/**
 * Characters reached by replacing one occurrence of [component] in the center.
 * [characters] keeps catalog order (frequency list order).
 */
data class Branch(
    val component: String,
    val characters: List<String>,
)
