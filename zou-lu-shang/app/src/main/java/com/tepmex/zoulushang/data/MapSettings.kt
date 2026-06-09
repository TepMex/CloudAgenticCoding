package com.tepmex.zoulushang.data

import com.tepmex.zoulushang.geo.TileMath

data class MapSettings(
    val showTakeoutGrid: Boolean = true,
    val showLiveGrid: Boolean = true,
    val gridZoom: Int = TileMath.DEFAULT_GRID_ZOOM,
)

data class SettingsDraft(
    val showTakeoutGrid: Boolean,
    val showLiveGrid: Boolean,
    val gridZoom: Int,
)
