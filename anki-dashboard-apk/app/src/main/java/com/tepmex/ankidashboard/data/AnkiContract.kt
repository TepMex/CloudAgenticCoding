package com.tepmex.ankidashboard.data

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
    val CARDS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "cards")
    val MODELS_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "models")
    val DECKS_ALL_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")

    const val NOTE_ID = "_id"
    const val NOTE_MID = "mid"
    const val NOTE_FLDS = "flds"
    const val NOTE_TAGS = "tags"

    const val MODEL_ID = "_id"
    const val MODEL_FIELD_NAMES = "field_names"

    const val DECK_ID = "deck_id"
    const val DECK_NAME = "deck_name"

    const val CARD_ID = "_id"
    const val CARD_NOTE_ID = "note_id"
    const val CARD_DECK_ID = "deck_id"
    const val CARD_INTERVAL = "interval"
    const val CARD_REPS = "reps"
    const val CARD_LAPSES = "lapses"
    const val CARD_LAST_REVIEW_TIME_SECS = "last_review_time_secs"
}
