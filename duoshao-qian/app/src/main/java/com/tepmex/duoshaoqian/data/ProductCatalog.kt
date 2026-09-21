package com.tepmex.duoshaoqian.data

data class Product(
    val id: String,
    val hanzi: String,
    val pinyin: String,
    val russian: String,
    val imageAsset: String,
    val priceYuanOptions: List<Int>,
)

object ProductCatalog {
    val all: List<Product> = listOf(
        Product(
            id = "water",
            hanzi = "矿泉水",
            pinyin = "kuàngquánshuǐ",
            russian = "минеральная вода",
            imageAsset = "products/product_water.jpg",
            priceYuanOptions = listOf(2, 3),
        ),
        Product(
            id = "soy_milk",
            hanzi = "豆浆",
            pinyin = "dòujiāng",
            russian = "соевое молоко",
            imageAsset = "products/product_soy_milk.jpg",
            priceYuanOptions = listOf(3, 4),
        ),
        Product(
            id = "baozi",
            hanzi = "包子",
            pinyin = "bāozi",
            russian = "баоцзы",
            imageAsset = "products/product_baozi.jpg",
            priceYuanOptions = listOf(6, 8),
        ),
        Product(
            id = "jianbing",
            hanzi = "煎饼",
            pinyin = "jiānbǐng",
            russian = "цзяньбин",
            imageAsset = "products/product_jianbing.jpg",
            priceYuanOptions = listOf(8, 10, 12),
        ),
        Product(
            id = "watermelon",
            hanzi = "西瓜",
            pinyin = "xīguā",
            russian = "арбуз",
            imageAsset = "products/product_watermelon.jpg",
            priceYuanOptions = listOf(5, 8, 10),
        ),
        Product(
            id = "mango",
            hanzi = "芒果",
            pinyin = "mángguǒ",
            russian = "манго",
            imageAsset = "products/product_mango.jpg",
            priceYuanOptions = listOf(8, 10, 12),
        ),
        Product(
            id = "milk_tea",
            hanzi = "奶茶",
            pinyin = "nǎichá",
            russian = "молочный чай",
            imageAsset = "products/product_milk_tea.jpg",
            priceYuanOptions = listOf(12, 15, 16, 18),
        ),
        Product(
            id = "skewers",
            hanzi = "串儿",
            pinyin = "chuànr",
            russian = "шашлычки",
            imageAsset = "products/product_skewers.jpg",
            priceYuanOptions = listOf(10, 12, 15),
        ),
        Product(
            id = "dumplings",
            hanzi = "饺子",
            pinyin = "jiǎozi",
            russian = "пельмени",
            imageAsset = "products/product_dumplings.jpg",
            priceYuanOptions = listOf(18, 22, 25, 28),
        ),
        Product(
            id = "tea_egg",
            hanzi = "茶叶蛋",
            pinyin = "cháyèdàn",
            russian = "чайное яйцо",
            imageAsset = "products/product_tea_egg.jpg",
            priceYuanOptions = listOf(1, 2),
        ),
        Product(
            id = "tanghulu",
            hanzi = "糖葫芦",
            pinyin = "tánghúlu",
            russian = "танхулу",
            imageAsset = "products/product_tanghulu.jpg",
            priceYuanOptions = listOf(7, 8, 9),
        ),
        Product(
            id = "fried_rice",
            hanzi = "炒饭",
            pinyin = "chǎofàn",
            russian = "жареный рис",
            imageAsset = "products/product_fried_rice.jpg",
            priceYuanOptions = listOf(16, 18, 20),
        ),
        Product(
            id = "beef_noodles",
            hanzi = "牛肉面",
            pinyin = "niúròumiàn",
            russian = "лапша с говядиной",
            imageAsset = "products/product_beef_noodles.jpg",
            priceYuanOptions = listOf(20, 22, 25),
        ),
        Product(
            id = "malatang",
            hanzi = "麻辣烫",
            pinyin = "málàtàng",
            russian = "малатан",
            imageAsset = "products/product_malatang.jpg",
            priceYuanOptions = listOf(25, 28, 30),
        ),
        Product(
            id = "roast_duck_rice",
            hanzi = "烤鸭饭",
            pinyin = "kǎoyāfàn",
            russian = "рис с уткой",
            imageAsset = "products/product_roast_duck_rice.jpg",
            priceYuanOptions = listOf(30, 35, 40),
        ),
    )

    fun playable(audioAmounts: Set<Int>): List<Product> {
        return all.mapNotNull { product ->
            val prices = product.priceYuanOptions.filter { it in audioAmounts }
            if (prices.isEmpty()) null else product.copy(priceYuanOptions = prices)
        }
    }
}
