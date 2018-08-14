package generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.Expression
import ru.spbstu.kspt.librarymigration.CallEdge
import ru.spbstu.kspt.librarymigration.TemplateEdge
import ru.spbstu.kspt.librarymigration.parser.ModelParser
import java.util.*

fun main(args: Array<String>) {
    generate()
}

fun prettyPrinter(string: String): String {
    var intend = 0
    val buffer = StringBuilder()
    for (char in string) {
        buffer.append(char)
        if (char == '(') {
            buffer.append('\n')
            intend++
            buffer.append(" ".repeat(intend * 2))
        } else if (char == ')') {
            intend--
        } else if (char == ',') {
            buffer.append('\n')
            buffer.append(" ".repeat(intend * 2 - 1))
        }
    }
    return buffer.toString()
}

class Test

fun generate() {
    val modelStream = Test().javaClass.classLoader.getResourceAsStream("Requests.lsl")
    val ast = ModelParser().parse(modelStream)
    val library = ModelParser().postprocess(ast)
    println(prettyPrinter(library.toString()))

//    val originalCall = library.edges.filterIsInstance<CallEdge>().single { it.methodName == "get" }
//    originalCall.isStatic = true

    val compilationUnit = CompilationUnit()
    val wrapperClass = compilationUnit
            .addClass(library.name + "Wrapper")
            .setPublic(true)
    wrapperClass.extendedTypes = NodeList(JavaParser.parseClassOrInterfaceType("ProcessDataExchange"))
    wrapperClass.addField("int", "idCounter")
    for (type in library.machineTypes.values.filter { it.contains('.') }) {
        val actualName = type.split('.').last().replace("$", "")
        val clazz = ClassOrInterfaceDeclaration(EnumSet.noneOf(Modifier::class.java), false, actualName)
        clazz.isStatic = true
        wrapperClass.addMember(clazz)
        val edges = library.edges.filter { library.machineTypes[it.src.machine] == type }
        for (edge in edges.filterIsInstance<CallEdge>()) {
            val method = clazz.addMethod(edge.methodName)
            val methodBody = method.createBody()
            methodBody.addStatement(JavaParser.parseStatement("makeRequest(exec = \"import requests; r = requests.get(\'\$url')\",\n" +
                    "store = \"r\", description = \"get\");"))
            if (edge.hasReturnValue) {
                val linkedEdge = edge.linkedEdge!!
                method.type = JavaParser.parseType(linkedEdge.dst.machine.name)
                methodBody.addStatement(JavaParser.parseStatement("receiveResponse();"))
            }
        }
        for (edge in edges.filterIsInstance<TemplateEdge>()) {
            val method = clazz.addMethod("to" + edge.dst.machine.name)
            method.type = JavaParser.parseType(edge.dst.machine.name)
        }
    }
//    myClass.addField(Int::class.javaPrimitiveType, "A_CONSTANT", Modifier.PUBLIC, Modifier.STATIC)
//    myClass.addField(String::class.java, "name", Modifier.PRIVATE)
    val code = wrapperClass.toString()
    println(code)
}