package com.tepmex.paircompelo.data.db

import androidx.room.TypeConverter
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import java.time.Instant
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun outcomeToString(value: ComparisonOutcome?): String? = value?.name

    @TypeConverter
    fun stringToOutcome(value: String?): ComparisonOutcome? =
        value?.let { ComparisonOutcome.valueOf(it) }

    @TypeConverter
    fun strategyToString(value: PairSelectionStrategy?): String? = value?.name

    @TypeConverter
    fun stringToStrategy(value: String?): PairSelectionStrategy? =
        value?.let { PairSelectionStrategy.valueOf(it) }
}
