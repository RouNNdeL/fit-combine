package com.roundel

import com.garmin.fit.Factory
import com.garmin.fit.LapMesg
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var file0: File? = null
    var file1: File? = null

    var dir: String? = null

    when (args.size) {
        0 -> {
            println("Attempting to load the latest 2 files from the current directory")
            dir = "."
        }
        1 -> dir = args[0]
        2 -> {
            file0 = File(args[0])
            file1 = File(args[1])
        }
        else -> {
            println("1 or 2 arguments expected (1: dir; 2: file0 file1)")
            exitProcess(-1)
        }
    }

    if (dir != null) {
        val files = getLatestFiles(dir)
        if (files == null || files.size < 2) {
            println("Directory does not contain 2 files")
            exitProcess(-1)
        }

        file0 = files[0]
        file1 = files[1]
    }

    if (file0 == null || file1 == null) {
        throw RuntimeException("Files should be initialized by this point")
    }

    val decoder0 = Decoder(file0)
    val decoder1 = Decoder(file1)

    decoder0.decode()
    decoder1.decode()

    if (decoder0.getRecords().isEmpty()) {
        println("File ${file0.name} does not contain any records")
        exitProcess(-1)
    }
    if (decoder1.getRecords().isEmpty()) {
        println("File ${file1.name} does not contain any records")
        exitProcess(-1)
    }

    println("Files:")
    println("1. ${file0.name}")
    println("2. ${file1.name}")
    println()
    println("What file should be the master (1 or 2)?")
    var masterFileSelection: Int? = null
    while (masterFileSelection == null) {
        val line = readLine() ?: exitProcess(-1)
        try {
            val int = line.toInt()
            if (int == 1 || int == 2) {
                masterFileSelection = int
            } else {
                println("Please input 1 or 2")
            }
        } catch (e: NumberFormatException) {
            println("Please input a valid number")
        }
    }

    val masterDecoder = if (masterFileSelection == 1) decoder0 else decoder1
    val slaveDecoder = if (masterFileSelection != 1) decoder0 else decoder1

    val masterFile = if (masterFileSelection == 1) file0 else file1
    // val slaveFile = if (masterFileSelection != 1) file0 else file1

    val combiner = Combiner(
        ArrayList(masterDecoder.getRecords()),
        ArrayList(masterDecoder.getAvailableFieldNums())
    )

    println("Attempting to detect time shift based on GPS path correlation (this may take a while)")
    var timeShift = combiner.detectTimeShift(ArrayList(slaveDecoder.getRecords()))

    if (timeShift == null) {
        println("Unable to detect time shift (no gps data or files are not from the same ride)")
        println("You can still input the time shift manually, but this is not recommended")
        while (timeShift == null) {
            val line = readLine() ?: exitProcess(-1)
            try {
                timeShift = line.toLong()
            } catch (e: NumberFormatException) {
                println("Please input a valid number")
            }
        }

    } else {
        println("Detected time shift: $timeShift")
    }
    println()

    val masterFieldNums = masterDecoder.getAvailableFieldNums()
    val slaveFieldNums = slaveDecoder.getAvailableFieldNums()


    println("File master contains following fields:")
    printFields(masterFieldNums)
    println()

    println("File slave contains following fields:")
    printFields(slaveFieldNums)

    println()
    println("What fields do you want to merge from slave into master? (comma separated numbers)")
    var fieldsToMerge: List<Int>? = null
    while (fieldsToMerge == null) {
        try {
            val line = readLine() ?: exitProcess(-1)
            val ints = line.split(",").map { it.trim().toInt() }
            if (ints.isEmpty()) {
                println("Please input at least on field")
                continue
            }

            if (!slaveFieldNums.containsAll(ints)) {
                println("Please input valid field numbers")
                continue
            }

            fieldsToMerge = ints
        } catch (e: NumberFormatException) {
            println("Please input valid field numbers")
        }

    }

    var average: Boolean? = null
    if (fieldsToMerge.any { masterFieldNums.contains(it) }) {
        println("Some fields you've chosen are present in the master. What merging strategy do you want to use?")
        println("1. Overwrite master with slave")
        println("2. Average master and slave")
        while (average == null) {
            val line = readLine() ?: exitProcess(-1)
            try {
                val int = line.toInt()
                if (int == 1 || int == 2) {
                    average = int == 2
                } else {
                    println("Please input 1 or 2")
                }
            } catch (e: NumberFormatException) {
                println("Please input a valid number")
            }
        }
    } else {
        average = false
    }

    var trim = true
    println("Do you want to trim the records to match the shorter activity? [Y/n]")
    if (readLine()?.toLowerCase() == "n") {
        trim = false
    }

    combiner.mergeRecords(ArrayList(slaveDecoder.getRecords()), fieldsToMerge, timeShift, average, trim)
    println("Records have been merged")
    println()

    println("What strategy should be used for missing values?")
    println("1. Interpolate")
    println("2. Set to 0")
    println("3. Remove (records that do not contain all required values will be removed)")
    println("4. Skip (values will still be missing)")
    var missingStrategy: Combiner.MissingStrategy? = null
    while (missingStrategy == null) {
        val line = readLine() ?: exitProcess(-1)
        try {
            val int = line.toInt()
            if (int in 1..3) {
                missingStrategy = when (int) {
                    1 -> Combiner.MissingStrategy.INTERPOLATE
                    2 -> Combiner.MissingStrategy.SET_ZERO
                    3 -> Combiner.MissingStrategy.REMOVE
                    4 -> Combiner.MissingStrategy.SKIP
                    else -> throw RuntimeException("Invalid missing strategy")
                }
            } else {
                println("Please input a number in rang 1-3")
            }
        } catch (e: NumberFormatException) {
            println("Please input a valid number")
        }
    }

    if (missingStrategy != Combiner.MissingStrategy.SKIP) {
        println("Those fields are present in the merged file:")
        printFields(combiner.fields.sorted())

        println("Which fields should be required in all records? (comma separated numbers)")
        var requiredFields: List<Int>? = null
        while (requiredFields == null) {
            requiredFields = try {
                val line = readLine() ?: exitProcess(-1)
                val ints = line.split(",").map { it.trim().toInt() }
                if (!combiner.fields.containsAll(ints)) {
                    println("Please input valid field numbers")
                    continue
                }

                ints
            } catch (e: NumberFormatException) {
                emptyList()
            }
        }

        combiner.cleanUp(requiredFields, missingStrategy)
    }

    println()
    val masterLaps = ArrayList<LapMesg>()
    val slaveLaps = ArrayList<LapMesg>()

    val masterHasLaps = masterDecoder.laps.size > 1
    val slaveHasLaps = slaveDecoder.laps.size > 1

    if (masterHasLaps && slaveHasLaps) {
        println("Both files have laps master: ${masterDecoder.laps.size}, slave: ${slaveDecoder.laps.size}")
        println("Only on of those can be selected to copy the laps from, " +
                "which one would you like to choose (none, master, slave)? [N/m/s]")
        if (readLine()?.toLowerCase() == "m") {
            masterLaps.addAll(masterDecoder.laps)
        } else if (readLine()?.toLowerCase() == "s") {
            slaveLaps.addAll(slaveDecoder.laps)
        }
    } else if(masterHasLaps) {
        println("Master file has ${masterDecoder.laps.size} laps. Do you want to copy those into the combined file? [Y/n]")
        if (readLine()?.toLowerCase() != "n") {
            masterLaps.addAll(masterDecoder.laps)
        }
    } else if (slaveHasLaps) {
        println("Slave file has ${slaveDecoder.laps.size} laps. Do you want to copy those into the combined file? [Y/n]")
        if (readLine()?.toLowerCase() != "n") {
            slaveLaps.addAll(slaveDecoder.laps)
        }
    }

    println()
    var saveDir = "."
    if (dir != null) {
        println("Looks like your files have been automatically loaded from a directory.")
        println("Do you want to save the resulting file there? [Y/n]")
        if (readLine()?.toLowerCase() != "n") {
            saveDir = dir
        }
    }

    println("Where do you want to save the file?")
    var savePath: String = readLine() ?: ""
    if (savePath.trim().isEmpty()) {
        val formatter = SimpleDateFormat("yyyy_MM_dd")
        savePath = "${formatter.format(masterDecoder.getActivityDate() ?: LocalDate.now())}_combined.fit"
    }

    val encoder = Encoder(File(saveDir, savePath), masterFile, combiner.records)
    encoder.addLaps(masterLaps)
    encoder.addLaps(slaveLaps, timeShift)
    encoder.encode()
}

fun getLatestFiles(dir: String, extension: String? = "fit", count: Int = 2): List<File>? {
    val files: Array<File> = File(dir).listFiles { file ->
        file != null && file.isFile && (extension == null || file.extension.toLowerCase() == "fit")
    } ?: return null
    return files.sortedByDescending { file -> file.lastModified() }.take(count)
}

fun printFields(fields: Iterable<Int>) {
    fields
        .filter { it != RecordMesg.TimestampFieldNum }
        .forEach { f -> println("  ${f}: ${Factory.createField(MesgNum.RECORD, f).name}") }
}