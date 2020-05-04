import com.garmin.fit.*
import java.io.File
import java.io.FileInputStream

class Decoder(private val file: File) : RecordMesgListener {
    private val records = ArrayList<Record>()

    private val fieldsCount = HashMap<Int, Int>()

    fun decode() {
        val decode = Decode()
        val messageBroadcaster = MesgBroadcaster(decode)

        messageBroadcaster.addListener(this)

        decode.read(FileInputStream(file), messageBroadcaster)
    }

    fun getRecords(): Collection<Record> {
        return records
    }

    fun getAvailableFields(threshold: Float = 0.9F): List<Int> {
        return fieldsCount
            .filter { it.value >= records.size * threshold }
            .map { it.key }
    }

    fun getAvailableFieldNames(threshold: Float = 0.9F): List<String> {
        return getAvailableFields(threshold)
            .map { Factory.createField(MesgNum.RECORD, it).name }
    }

    override fun onMesg(mesg: RecordMesg?) {
        if (mesg == null) {
            return
        }

        Record.fromFitMessage(mesg)?.let { record ->
            records.add(record)
            record.fields.forEach { f ->
                fieldsCount.merge(f, 1, Int::plus)
            }
        }
    }
}