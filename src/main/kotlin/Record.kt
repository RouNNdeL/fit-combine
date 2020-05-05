import com.garmin.fit.*
import kotlin.math.pow

data class Record(
    val timestamp: Long,
    private val originalRecord: RecordMesg
) {
    companion object {
        fun fromFitMessage(msg: RecordMesg): Record? {
            val timestamp = msg.getField(RecordMesg.TimestampFieldNum).longValue ?: return null

            return Record(timestamp, msg)
        }

        fun averageFields(f0: Field, f1: Field) {
            if (f0.type != f1.type) {
                throw IllegalArgumentException("Both fields have to have the same type")
            }

            f0.value = when (f0.type) {
                Fit.BASE_TYPE_UINT8,
                Fit.BASE_TYPE_UINT8Z,
                Fit.BASE_TYPE_UINT16,
                Fit.BASE_TYPE_UINT16Z -> (f0.integerValue + f1.integerValue) / 2
                Fit.BASE_TYPE_UINT32,
                Fit.BASE_TYPE_UINT32Z,
                Fit.BASE_TYPE_UINT64,
                Fit.BASE_TYPE_UINT64Z -> (f0.longValue + f1.longValue) / 2
                Fit.BASE_TYPE_FLOAT32 -> (f0.integerValue + f1.integerValue) / 2
                Fit.BASE_TYPE_FLOAT64 -> (f0.doubleValue + f1.doubleValue) / 2

                else -> throw UnsupportedOperationException("Unexpected fields type ${f0.type}")
            }
        }
    }

    fun hasAllFields(fields: Iterable<Int>): Boolean {
        return fields.all { hasField(it) }
    }

    fun hasField(field: Int): Boolean {
        return originalRecord.hasField(field)
    }

    fun getFields(): List<Int> {
        return originalRecord.fields.map { it.num }
    }

    fun setField(field: Int, value: Any) {
        if (!originalRecord.hasField(field)) {
            val newField = Factory.createField(MesgNum.RECORD, field)
            newField.value = value
            originalRecord.addField(newField)
        } else {
            originalRecord.getField(field).value = value
        }
    }

    fun getOriginalRecord(): RecordMesg {
        return RecordMesg(originalRecord)
    }

    fun interpolate(field: Int, start: Record, end: Record, fraction: Float) {
        val startField = start.originalRecord.getField(field)
        val endField = end.originalRecord.getField(field)

        val interpolatedField = Factory.createField(MesgNum.RECORD, field)
        interpolatedField.value = when (interpolatedField.type) {
            Fit.BASE_TYPE_UINT8,
            Fit.BASE_TYPE_UINT8Z,
            Fit.BASE_TYPE_UINT16,
            Fit.BASE_TYPE_UINT16Z ->
                (startField.integerValue + (endField.integerValue - startField.integerValue) * fraction).toInt()
            Fit.BASE_TYPE_UINT32,
            Fit.BASE_TYPE_UINT32Z,
            Fit.BASE_TYPE_UINT64,
            Fit.BASE_TYPE_UINT64Z ->
                (startField.longValue + (endField.longValue - startField.longValue) * fraction).toLong()
            Fit.BASE_TYPE_FLOAT32 ->
                (startField.floatValue + (endField.floatValue - startField.floatValue) * fraction)
            Fit.BASE_TYPE_FLOAT64 ->
                (startField.doubleValue + (endField.doubleValue - startField.doubleValue) * fraction)

            else -> 0
        }

        originalRecord.addField(interpolatedField)
    }

    fun combine(record: Record, fields: Iterable<Int>, average: Boolean = false) {
        val sourceRecord = record.originalRecord
        fields.forEach {
            if (sourceRecord.hasField(it)) {
                if (average && originalRecord.hasField(it)) {
                    averageFields(originalRecord.getField(it), sourceRecord.getField(it))
                } else {
                    originalRecord.setField(sourceRecord.getField(it))
                }
            }
        }
    }

    fun hasPosition(): Boolean {
        return originalRecord.hasField(RecordMesg.PositionLatFieldNum) &&
                originalRecord.hasField(RecordMesg.PositionLongFieldNum)
    }

    fun errorWith(record: Record): Double {
        return (record.originalRecord.positionLong - originalRecord.positionLong).toDouble().pow(2) +
                (record.originalRecord.positionLat - originalRecord.positionLat).toDouble().pow(2)
    }

    override fun toString(): String {
        return originalRecord.fields
            .filter { it.num == RecordMesg.HeartRateFieldNum || it.num == RecordMesg.CadenceFieldNum }
            .joinToString("\t") { "${it.value}" }
    }
}