package com.roundel

import com.garmin.fit.*
import java.io.File
import java.io.FileInputStream

class Encoder(destinationFile: File, private val masterFile: File, private val records: List<Record>) :
    FileIdMesgListener {
    private val encode: FileEncoder = FileEncoder(destinationFile, Fit.ProtocolVersion.V2_0)
    private val laps = ArrayList<LapMesg>()

    fun encode() {
        val decode = Decode()
        val messageBroadcaster = MesgBroadcaster(decode)

        messageBroadcaster.addListener(this)
        decode.read(FileInputStream(masterFile), messageBroadcaster)

        laps.forEach { encode.write(it) }

        records.forEach {
            encode.write(it.getOriginalRecord())
        }

        encode.close()
    }

    fun addLaps(laps: ArrayList<LapMesg>, timeShift: Long = 0) {
        if(laps.isEmpty()) {
            return
        }

        val firstLap = LapMesg(laps[0])
        firstLap.startTime = DateTime(records[0].timestamp)
        this.laps.add(firstLap)

        laps.drop(1).forEach {
            val msg = LapMesg(it)
            msg.startTime.add(timeShift)
            this.laps.add(msg)
        }
    }

    override fun onMesg(msg: FileIdMesg?) {
        encode.write(msg)
    }
}