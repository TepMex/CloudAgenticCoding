package com.tepmex.byokassistedreader.domain

object Prompts {
    val stpvoSystem: String = """
        You segment Mandarin sentences into STPVO roles for a learner.
        Roles:
        - subject: who (Кто)
        - time: when (Когда)
        - place: where (Где), include 在/从 when it belongs to the place
        - verb: what they do (Что делает)
        - object: what or whom (С чем)
        A sentence may omit roles. Each span is an exact contiguous substring of that sentence.
        Spans must not overlap. Do not add characters. Do not translate.
        Return JSON only:
        {"sentences":[{"text":"...","parts":[{"role":"subject","text":"..."}]}]}
    """.trimIndent()

    fun stpvoUser(sentences: List<String>): String = buildString {
        appendLine("Разбери каждое предложение.")
        sentences.forEach { appendLine(it) }
    }.trim()

    fun glossSystem(russian: Boolean): String = if (russian) {
        """
        You help a Russian learner of Chinese.
        From the page, list words that are not in the known-word list, and every chengyu (成语) that appears.
        Explanations are short Russian. Do not explain a known word unless it is a chengyu.
        Copy each word exactly from the page.
        Return JSON only:
        {"words":[{"word":"...","explanation":"..."}],"chengyu":[{"word":"...","explanation":"..."}]}
        """.trimIndent()
    } else {
        """
        你帮助学中文的人。
        只解释这一页里、不在已知词表中的词，以及页上出现的成语。
        解释用最简单的中文，短句，避免难字。不要写俄语。
        词语必须是页面里的原文。已知词如果不是成语就不要解释。
        只返回 JSON：
        {"words":[{"word":"...","explanation":"..."}],"chengyu":[{"word":"...","explanation":"..."}]}
        """.trimIndent()
    }

    fun glossUser(pageText: String, knownWords: List<String>): String = buildString {
        appendLine("已知词：")
        if (knownWords.isEmpty()) appendLine("（无）") else knownWords.forEach { appendLine(it) }
        appendLine("页面：")
        append(pageText)
    }
}
