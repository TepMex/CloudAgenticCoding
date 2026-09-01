package com.tepmex.wodeluyou.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryStyle(
    val icon: ImageVector,
    val accent: Color,
)

object CategoryStyles {
    private val fallback = CategoryStyle(Icons.Outlined.Translate, Color(0xFFC23A2B))

    private val byName = mapOf(
        "Населённый пункт" to CategoryStyle(Icons.Outlined.LocationCity, Color(0xFFC23A2B)),
        "Регион" to CategoryStyle(Icons.Outlined.Map, Color(0xFFB4532A)),
        "География" to CategoryStyle(Icons.Outlined.Terrain, Color(0xFF3F6B4A)),
        "Достопримечательность" to CategoryStyle(Icons.Outlined.Museum, Color(0xFF8B1E18)),
        "Место / рынок" to CategoryStyle(Icons.Outlined.Storefront, Color(0xFFC2781E)),
        "Транспорт / место" to CategoryStyle(Icons.Outlined.Train, Color(0xFF2F5D8A)),
        "Место / улица" to CategoryStyle(Icons.Outlined.Signpost, Color(0xFF6B4A2F)),
        "Еда / заведение" to CategoryStyle(Icons.Outlined.Restaurant, Color(0xFFC23A2B)),
        "Еда" to CategoryStyle(Icons.Outlined.RestaurantMenu, Color(0xFFD35400)),
        "Еда / напиток" to CategoryStyle(Icons.Outlined.LocalCafe, Color(0xFF8D4B2B)),
        "Напиток" to CategoryStyle(Icons.Outlined.LocalBar, Color(0xFF7A3E6A)),
        "Транспорт" to CategoryStyle(Icons.Outlined.Train, Color(0xFF2F5D8A)),
        "Навигация" to CategoryStyle(Icons.Outlined.Explore, Color(0xFF1F6B5C)),
        "Багаж" to CategoryStyle(Icons.Outlined.Luggage, Color(0xFF4A5568)),
        "Документы" to CategoryStyle(Icons.Outlined.Badge, Color(0xFF3D4F7A)),
        "Жильё" to CategoryStyle(Icons.Outlined.Hotel, Color(0xFF6B3F6A)),
        "Быт" to CategoryStyle(Icons.Outlined.LocalPharmacy, Color(0xFF3F6B4A)),
        "Покупки" to CategoryStyle(Icons.Outlined.ShoppingBag, Color(0xFFC2781E)),
        "Ресторан" to CategoryStyle(Icons.Outlined.RamenDining, Color(0xFFC23A2B)),
        "Фраза" to CategoryStyle(Icons.Outlined.ChatBubbleOutline, Color(0xFF2F5D8A)),
    )

    fun of(category: String): CategoryStyle = byName[category] ?: fallback
}
