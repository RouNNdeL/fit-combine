import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Combiner(val records: MutableList<Record>, val fields: MutableList<Int>) {
       init {
        if (records.isEmpty()) {
            throw IllegalArgumentException("Records may not be empty")
        }
    }

    fun cleanUp(
        requiredFields: List<Int>,
        missingStrategy: MissingStrategy = MissingStrategy.INTERPOLATE
    ) {
        if(missingStrategy == MissingStrategy.SKIP) {
            return
        }

        requiredFields.forEach {
            var lastRecord: Record? = null
            var i = 0

            loop@ while (i < records.size) {
                val record = records[i]

                if (!record.hasAllFields(requiredFields)) {
                    when (missingStrategy) {
                        MissingStrategy.INTERPOLATE -> {
                            if (lastRecord == null) {
                                i++
                                continue@loop
                            }

                            val missingCount = interpolate(it, records, i - 1) ?: break@loop
                            i += missingCount
                        }
                        MissingStrategy.REMOVE -> {
                            records.remove(record)
                        }
                        MissingStrategy.SET_ZERO -> {
                            record.setField(it, 0)
                        }
                        MissingStrategy.SKIP -> {}
                    }
                } else {
                    lastRecord = record
                }

                i++
            }

        }
    }

    fun mergeRecords(
        otherRecords: List<Record>,
        fields: List<Int>,
        timeShift: Long = detectTimeShift(otherRecords) ?: 0,
        average: Boolean = false,
        trimRecords: Boolean = true
    ) {
        var i = 0
        var j = 0

        val startTimestamp = max(records.first().timestamp, otherRecords.first().timestamp + timeShift)
        val endTimestamp = min(records.last().timestamp, otherRecords.last().timestamp + timeShift)

        while (i < records.size && j < otherRecords.size) {
            val otherTimestampShifted = otherRecords[j].timestamp + timeShift
            val timestamp = records[i].timestamp

            if (trimRecords) {
                if (timestamp < startTimestamp || timestamp > endTimestamp) {
                    i++
                    continue
                }

                if (otherTimestampShifted < startTimestamp || otherTimestampShifted > endTimestamp) {
                    j++
                    continue
                }
            }

            when {
                timestamp > otherTimestampShifted -> {
                    j++
                }

                timestamp < otherTimestampShifted -> {
                    i++
                }

                else -> {
                    records[i].combine(otherRecords[j], fields, average)
                    i++
                    j++
                }
            }
        }

        this.fields.addAll(fields)
    }

    fun detectTimeShift(
        otherRecords: List<Record>,
        minOverlap: Float = 0.8F,
        maxStdDev: Double = 10_000.0
    ): Long? {
        if (minOverlap > 1 || minOverlap < 0) {
            throw IllegalArgumentException("minOverlap has to be in range 0-1")
        }

        if (otherRecords.isEmpty()) {
            throw IllegalArgumentException("Records may not be empty")
        }

        val last0 = records.last().timestamp
        val first0 = records.first().timestamp
        val last1 = otherRecords.last().timestamp
        val first1 = otherRecords.first().timestamp

        val maxOffset: Int = ((1 - minOverlap) * min(
            last0 - first0,
            last1 - first1
        )).roundToInt()

        val startShift = first0 - first1 - maxOffset
        val endShift = last0 - last1 + maxOffset

        val shiftMap = HashMap<Long, Double>()
        var shift = startShift

        while (shift <= endShift) {
            val stdDev = getStdDevForTimeShift(otherRecords, shift)
            shiftMap[shift] = stdDev
            shift++
        }

        val guessedShift = shiftMap.minBy { it.value } ?: return null

        if (guessedShift.value > maxStdDev) {
            return null
        }

        return guessedShift.key
    }

    private fun getStdDevForTimeShift(
        otherRecords: List<Record>,
        shift: Long
    ): Double {
        var i = 0
        var j = 0
        var devSum = 0.0
        var recordCount = 0

        while (i < records.size && j < otherRecords.size) {
            if (records[i].timestamp > otherRecords[j].timestamp + shift) {
                j++
            } else if (records[i].timestamp < otherRecords[j].timestamp + shift) {
                i++
            } else if (records[i].hasPosition() && otherRecords[j].hasPosition()) {
                devSum += records[i].errorWith(otherRecords[j])
                recordCount++
                i++
                j++
            } else {
                i++
                j++
            }
        }

        return if (recordCount == 0) {
            Double.POSITIVE_INFINITY
        } else {
            sqrt(devSum / recordCount)
        }
    }

    private fun interpolate(field: Int, records: List<Record>, startIndex: Int): Int? {
        var i = startIndex

        do {
            i++
            if (i == records.size) {
                return null
            }
        } while (!records[i].hasField(field))

        val startRecord = records[startIndex]
        val endRecord = records[i]
        val missingCount = i - startIndex - 1

        for (j in 1..missingCount) {
            val fraction = j.toFloat() / (missingCount + 1)
            records[startIndex + j].interpolate(field, startRecord, endRecord, fraction)
        }

        return missingCount
    }

    enum class MissingStrategy {
        SKIP,
        REMOVE,
        INTERPOLATE,
        SET_ZERO,
    }
}