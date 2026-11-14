package dev.eareye.t.collect

import dev.eareye.t.domain.AudioCategorySelection

class CategoryCache {
    @Volatile
    var selection: AudioCategorySelection = AudioCategorySelection()
}
