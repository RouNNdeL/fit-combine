import java.io.File

fun main(args: Array<String>) {
    val decoder0 = Decoder(File(args[0]))
    val decoder1 = Decoder(File(args[1]))

    decoder0.decode()
    decoder1.decode()

    val combiner = Combiner(ArrayList(decoder0.getRecords()))

    val shift = combiner.detectTimeShift(ArrayList(decoder1.getRecords()))
    println(shift)
}