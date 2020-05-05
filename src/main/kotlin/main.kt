import com.garmin.fit.Factory
import com.garmin.fit.MesgNum
import java.io.File
import java.lang.RuntimeException

fun main(args: Array<String>) {
    val file0 = args[0]
    val file1 = args[1]

    val decoder0 = Decoder(File(file0))
    val decoder1 = Decoder(File(file1))

    decoder0.decode()
    decoder1.decode()

    println("Files:")
    println("1. ${file0.split(File.separator).last()}")
    println("2. ${file1.split(File.separator).last()}")
    println()
    println("What file should be the master (1 or 2)?")
    var masterFile: Int? = null
    while (masterFile == null) {
        val line = readLine()!!
        try {
            val int = line.toInt()
            if (int == 1 || int == 2) {
                masterFile = int
            } else {
                println("Please input 1 or 2")
            }
        } catch (e: NumberFormatException) {
            println("Please input a valid number")
        }
    }

    val masterDecoder = if (masterFile == 1) decoder0 else decoder1
    val slaveDecoder = if (masterFile != 1) decoder0 else decoder1

    val combiner = Combiner(
        ArrayList(masterDecoder.getRecords()),
        ArrayList(masterDecoder.getAvailableFieldNums())
    )

    println("Attempting to detect time shift based on GPS path correlation")
    var timeShift = combiner.detectTimeShift(ArrayList(slaveDecoder.getRecords()))

    if (timeShift == null) {
        println("Unable to detect time shift. Please input desired time shift below")
        while (timeShift == null) {
            val line = readLine()!!
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
    printFields( masterFieldNums)
    println()

    println("File slave contains following fields:")
    printFields(slaveFieldNums)

    println()
    println("What fields do you want to merge from slave into master? (comma separated numbers)")
    var fieldsToMerge: List<Int>? = null
    while (fieldsToMerge == null) {
        val ints = readLine()!!.split(",").map { it.trim().toInt() }
        if (ints.isEmpty()) {
            println("Please input at least on field")
            continue
        }

        if (!slaveFieldNums.containsAll(ints)) {
            println("Please input valid field numbers")
            continue
        }

        fieldsToMerge = ints
    }

    var average: Boolean? = null
    if (fieldsToMerge.any { masterFieldNums.contains(it) }) {
        println("Some fields you've chosen are present in the master. What merging strategy do you want to use?")
        println("1. Overwrite master with slave")
        println("2. Average master and slave")
        while (average == null) {
            val line = readLine()!!
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

    combiner.mergeRecords(ArrayList(slaveDecoder.getRecords()), fieldsToMerge, timeShift, average)
    println("Records have been merged")
    println()

    println("What strategy should be used for missing values?")
    println("1. Interpolate")
    println("2. Set to 0")
    println("3. Remove (records that do not contain all required values will be removed)")
    println("4. Skip (values will still be missing)")
    var missingStrategy: Combiner.MissingStrategy? = null
    while (missingStrategy == null) {
        val line = readLine()!!
        try {
            val int = line.toInt()
            if (int in 1..3) {
                missingStrategy = when(int) {
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

    if(missingStrategy != Combiner.MissingStrategy.SKIP) {
        println("Those fields are present in the merged file:")
        printFields(combiner.fields.sorted())

        println("Which fields should be required in all records? (comma separated numbers)")
        var requiredFields: List<Int>? = null
        while (requiredFields == null) {
            val ints = readLine()!!.split(",").map { it.trim().toInt() }
            if(!combiner.fields.containsAll(ints)) {
                println("Please input valid field numbers")
                continue
            }

            requiredFields = ints
        }

        combiner.cleanUp(requiredFields, missingStrategy)
    }



}

fun printFields(fields: Iterable<Int>) {
    fields.forEach { f -> println("  ${f}: ${Factory.createField(MesgNum.RECORD, f).name}") }
}