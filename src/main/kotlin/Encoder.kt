import com.garmin.fit.*
import java.io.File
import java.io.FileInputStream

class Encoder(destinationFile: File, private val masterFile: File, val records: List<Record>) : FileIdMesgListener {

    private val encode: FileEncoder = FileEncoder(destinationFile, Fit.ProtocolVersion.V2_0)

    fun encode() {
        val decode = Decode()
        val messageBroadcaster = MesgBroadcaster(decode)

        messageBroadcaster.addListener(this)
        decode.read(FileInputStream(masterFile), messageBroadcaster)

        records.forEach {
            encode.write(it.getOriginalRecord())
        }

        encode.close()
    }

    override fun onMesg(msg: FileIdMesg?) {
        encode.write(msg)
    }
}