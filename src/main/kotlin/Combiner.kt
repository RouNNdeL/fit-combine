import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Combiner(private val records: ArrayList<Record>) {
    init {
        if (records.isEmpty()) {
            throw IllegalArgumentException("Records may not be empty")
        }
    }

    fun detectTimeShift(
        otherRecords: ArrayList<Record>,
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
        otherRecords: ArrayList<Record>,
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
}