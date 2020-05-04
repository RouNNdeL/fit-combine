import com.garmin.fit.RecordMesg
import kotlin.math.pow

data class Record(
    val timestamp: Long,
    private val originalRecord: RecordMesg
) {
    val fields: List<Int> = originalRecord.fields
        .map { it.num }
        .filter { it != RecordMesg.TimestampFieldNum }

    companion object {
        fun fromFitMessage(msg: RecordMesg): Record? {
            val timestamp = msg.getField(RecordMesg.TimestampFieldNum).longValue ?: return null

            return Record(timestamp, msg)
        }
    }

    fun combine(msg: RecordMesg, fields: Iterable<Int>) {
        fields.forEach {
            originalRecord.setField(msg.getField(it))
        }
    }

    fun hasPosition(): Boolean {
        return fields.contains(RecordMesg.PositionLatFieldNum) &&
                fields.contains(RecordMesg.PositionLongFieldNum)
    }

    fun errorWith(record: Record): Double {
        return (record.originalRecord.positionLong - originalRecord.positionLong).toDouble().pow(2) +
                (record.originalRecord.positionLat - originalRecord.positionLat).toDouble().pow(2)
    }
}