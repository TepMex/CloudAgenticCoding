package com.tepmex.hanziinfogf14.data

class HanziCatalog(
    val componentsByHanzi: Map<String, List<String>>,
    val branchesByHanzi: Map<String, List<Branch>>,
) {
    fun contains(hanzi: String): Boolean = hanzi in componentsByHanzi

    fun componentsOf(hanzi: String): List<String> = componentsByHanzi[hanzi].orEmpty()

    fun branchesOf(hanzi: String): List<Branch> = branchesByHanzi[hanzi].orEmpty()
}
