package com.tepmex.ankidroidllm.data

import android.net.Uri

/**
 * AnkiDroid [FlashCardsContract](https://github.com/ankidroid/Anki-Android/blob/main/api/src/main/java/com/ichi2/anki/FlashCardsContract.kt)
 * URIs and columns (duplicated here to avoid a fragile JitPack dependency on the full Anki-Android tree).
 */
object AnkiContract {
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    const val READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")
    val NOTES_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")
    val MODELS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "models")
    val DECKS_ALL_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")

    const val NOTE_ID = "_id"
    const val NOTE_MID = "mid"
    const val NOTE_FLDS = "flds"

    const val MODEL_ID = "_id"
    const val MODEL_FIELD_NAMES = "field_names"

    const val DECK_ID = "deck_id"
    const val DECK_NAME = "deck_name"
}
