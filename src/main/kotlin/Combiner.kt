import kotlin.math.sqrt

class Combiner(private val records: ArrayList<Record>) {
    fun detectTimeShift(
        otherRecords: ArrayList<Record>,
        startShift: Long = 3 * 60 * 60,
        endShift: Long = startShift,
        maxStdDev: Double = 10_000.0
    ): Long? {
        val shiftMap = HashMap<Long, Double>()
        var shift = -startShift

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
        shift: Long, minOverlap: Float = 0.8F
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

        if (recordCount < otherRecords.size * minOverlap) {
            return Double.POSITIVE_INFINITY
        }

        return sqrt(devSum / recordCount)
    }
}