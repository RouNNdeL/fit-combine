package com.roundel

import com.garmin.fit.*
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Decoder(private val file: File) : RecordMesgListener, LapMesgListener, SegmentLapMesgListener {
    private val records = ArrayList<Record>()
    val laps = ArrayList<LapMesg>()

    private val fieldsCount = HashMap<Int, Int>()

    fun decode() {
        val decode = Decode()
        val messageBroadcaster = MesgBroadcaster(decode)

        messageBroadcaster.addListener(this as RecordMesgListener)
        messageBroadcaster.addListener(this as LapMesgListener)
        messageBroadcaster.addListener(this as SegmentLapMesgListener)

        decode.read(FileInputStream(file), messageBroadcaster)
    }

    fun getRecords(): Collection<Record> {
        return records
    }

    fun getAvailableFieldNums(threshold: Float = 0.75F): List<Int> {
        return fieldsCount
            .filter { it.value >= records.size * threshold }
            .map { it.key }
            .sorted()
    }

    fun getActivityDate(): Date? {
        return if (records.isEmpty()) {
            null
        } else {
            DateTime(records[0].timestamp).date
        }
    }

    fun getAvailableFieldNames(threshold: Float = 0.75F): List<String> {
        return getAvailableFieldNums(threshold)
            .map { Factory.createField(MesgNum.RECORD, it).name }
    }

    override fun onMesg(mesg: RecordMesg?) {
        if (mesg == null) {
            return
        }

        Record.fromFitMessage(mesg)?.let { record ->
            records.add(record)
            record.getFields().forEach { f ->
                fieldsCount.merge(f, 1, Int::plus)
            }
        }
    }

    override fun onMesg(msg: LapMesg?) {
        msg?.let {
            laps.add(it)
        }
    }

    override fun onMesg(mesg: SegmentLapMesg?) {
        println(mesg)
    }
}