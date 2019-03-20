import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import ru.spbstu.kspt.librarylink.MethodCallRequest
import ru.spbstu.kspt.librarymigration.parser.ModelParser
import ru.spbstu.kspt.librarymigration.parser.edgemodel.CallEdge
import ru.spbstu.kspt.librarymigration.parser.edgemodel.Edge
import ru.spbstu.kspt.librarymigration.parser.edgemodel.LinkedEdge
import java.io.File

fun main(args: Array<String>) {
    val logReader = File("link.log").bufferedReader()
    val cacheInstructionsWriter = File("LinkCache.cfg").bufferedWriter()
    val mapper = ObjectMapper().registerModule(KotlinModule())
    val modelStream = File(args.first()).inputStream()
    val ast = ModelParser().parse(modelStream)
    val library = ModelParser().postprocess(ast)

    val previousEdges = mutableListOf<Edge>()

    for (line in logReader.lineSequence()) {
        if (line.startsWith("{\"delete\"")) {
            continue
        }
        println("Line is $line")
        val request = mapper.readValue(line, MethodCallRequest::class.java)
        val edge = library.edges.filterIsInstance<CallEdge>().firstOrNull { it.methodName == request.methodName }
        println("Edge is $edge")

        if (edge != null) {
            for (previousEdge in previousEdges) {
                if (previousEdge.dst == edge.src) {
                    println("HIT!!!")
                    val realEdge = when (previousEdge) {
                        is CallEdge -> previousEdge
                        is LinkedEdge -> previousEdge.edge as CallEdge // TODO
                        else -> TODO()
                    }
                    cacheInstructionsWriter.write("${edge.methodName} ${realEdge.methodName}")
                    cacheInstructionsWriter.flush()
                }
            }

            previousEdges.clear()

            previousEdges += edge
            val linkedEdge = edge.linkedEdge
            if (linkedEdge != null) {
                previousEdges += linkedEdge
            }
        }
    }
    logReader.close()
    cacheInstructionsWriter.flush()
    cacheInstructionsWriter.close()
}