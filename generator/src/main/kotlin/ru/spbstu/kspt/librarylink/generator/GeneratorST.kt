package ru.spbstu.kspt.librarylink.generator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import org.stringtemplate.v4.ST
import org.stringtemplate.v4.STGroupFile
import ru.spbstu.kspt.librarylink.MethodCallRequest
import ru.spbstu.kspt.librarylink.Request
import ru.spbstu.kspt.librarymigration.parser.FunctionArgument
import ru.spbstu.kspt.librarymigration.parser.LibraryDecl
import ru.spbstu.kspt.librarymigration.parser.ModelParser
import java.io.File
import java.io.InputStream

val primitiveTypes = listOf("String", "int", "Int")
val supportsOOP = false

private fun generateArgs(args: List<FunctionArgument>, types: Map<String, String>): List<Arg> {
    val validArgs = if (supportsOOP) args.filterNot { it.type == "self" } else args
    val generated = validArgs.map {
        Arg(type = checkNotNull(types[it.type]),
                name = it.name, reference = calcIsReference(types[it.type]!!), self = it.type == "self")
    } // TODO
    generated.filter { it.self }.withIndex().forEach { it.value.index = it.index }
    return generated
}

fun generateST(library: LibraryDecl, st: ST) {
    val methods = mutableMapOf<String, Method>()

    val types = library.types.map { it.semanticType to it.codeType }.toMap()

    for (function in library.functions) {
        val hasSelf = function.args.any { it.type == "self" }
        val static = function.staticName != null || !hasSelf
        val objectId = if (static) function.staticName?.staticName ?: "" else "assignedID"
        val property = function.properties.any { it.key == "type" && it.value == "get" }
        val args = generateArgs(function.args, types + ("self" to function.entity))
        val request = MethodCallRequest(
                methodName = function.name,
                objectID = objectId,
                args = listOf(),
                doGetReturnValue = function.returnValue != null,
                isProperty = property,
                isStatic = static)
        val method = Method(function.name, args)
        method.request = request
        method.returnValue = types[function.returnValue]
        method.referenceReturn = method.returnValue !in primitiveTypes
        methods += method.name to method
    }

    for (automaton in library.automata) {
        val classMethods = automaton.shifts.filterNot { it.from == "Created" }.flatMap { it.functions }.map { checkNotNull(methods[it]) }
        val constructor = automaton.shifts.filter { it.from == "Created" }.flatMap { it.functions }.map { checkNotNull(methods[it]) }.singleOrNull()
        val clazz = WrappedClass(
                automaton.name,
                classMethods,
                constructor
        )
        st.add("wrappedClasses", clazz)
    }
    st.add("libraryName", library.name)
//    val wrappedClass = WrappedClass(
//            "Requests",
//            listOf(
//                    Method("get", listOf(
//                            Arg("String", "url", "InPlaceArgument")
//                    )),
//                    Method("get", listOf(
//                            Arg("String", "url", "InPlaceArgument"),
//                            Arg("Headers", "headers", "PersistenceArgument")
//                    ))
//            )
//    )
//    st.add("wrappedClasses", wrappedClass)

//    for (type in library.machineTypes.values.filter { it.contains('.') }) {
//        val actualName = getRealClassName(type)
////        if (type != "Requests.Requests") { // TODO: Very dirty!
////            val constructor = clazz.addConstructor()
////            constructor.addParameter(JavaParser.parseType("String"), "storedName")
////            constructor.createBody().addStatement(JavaParser.parseStatement("super(storedName);"))
////        }
//        val edges = library.edges.filter { library.machineTypes[it.src.machine] == type }
//        for (edge in edges.filterIsInstance<CallEdge>()) {
//            val request = makeRequest(edge)
//            for (arg in request.args) {
//                method.addParameter(JavaParser.parseType(getVariableType(arg.param)), arg.value.toString())
//            }
//            val statements = generateStatements(request, edge.linkedEdge?.dst?.machine?.type())
//            for (s in statements) {
//                methodBody.addStatement(s)
//            }
//            if (edge.hasReturnValue) {
//                val linkedEdge = edge.linkedEdge!!
//                method.type = JavaParser.parseType(linkedEdge.dst.machine.type())
//            }
//        }
//        for (edge in edges.filterIsInstance<TemplateEdge>()) {
//            val method = clazz.addMethod("to" + edge.dst.machine.name)
//            method.type = JavaParser.parseType(edge.dst.machine.name)
//        }
//    }
////    myClass.addField(Int::class.javaPrimitiveType, "A_CONSTANT", Modifier.PUBLIC, Modifier.STATIC)
////    myClass.addField(String::class.java, "name", Modifier.PRIVATE)
//    val code = compilationUnit.toString()
//    println(code)
}

fun calcIsReference(type: String) = type !in primitiveTypes

data class WrappedClass(val name: String, val methods: List<Method>, val constructor: Method?)

data class Method(val name: String, val args: List<Arg>) {
    var request: Request = MethodCallRequest("methodName", "ObjectID", listOf(),  true, false, true)
    var returnValue: String? = null
    var referenceReturn: Boolean = true
}

data class Arg(val type: String, val name: String, val reference: Boolean, val self: Boolean) {
    var index = 0
}

fun InputStream.parseModel() = ModelParser().parse(this)

class GeneratorCommand : CliktCommand() {
    val language: String by option("-l", "--language", help="Receiver language").choice(*supportedLanguages()).required()
    val output: File? by option("-o", "--output", help = "Output file").file()
    val models: List<File> by argument(help="Models").file(exists = true).multiple(required = true).validate {
        require(it.isNotEmpty()) { "At least one model should be specified" }
    }

    override fun run() = generateWrapper(
            template = "generator/$language.stg",
            modelFiles = models,
            outputFile = output
    )

    private fun resourceReader(name: String) = this.javaClass.classLoader.getResourceAsStream(name).bufferedReader()

    private fun supportedLanguages() =
            resourceReader("generator/SupportedLanguages.txt").readLines().toTypedArray()
}

fun main(args: Array<String>) = GeneratorCommand().main(args)

private fun generateWrapper(template: String, modelFiles: List<File>, outputFile: File?) {
    val group = STGroupFile(template)
    val st = group.getInstanceOf("wrapperClass")
//    st.add("type", "int")
//    val wrappedClass = WrappedClass(
//            "Requests",
//            listOf(
//                    Method("get", listOf(
//                            Arg("String", "url", "InPlaceArgument")
//                    )),
//                    Method("get", listOf(
//                            Arg("String", "url", "InPlaceArgument"),
//                            Arg("Headers", "headers", "PersistenceArgument")
//                    ))
//            )
//    )
//    st.add("wrappedClasses", wrappedClass)
//    st.add("value", 0)
    val models = modelFiles.map { it.inputStream().parseModel() }
    val mergedAST = mergeASTs(models.first().name, models)
    generateST(mergedAST, st)
    val result = st.render()
    if (outputFile != null) {
        outputFile.writeText(result)
    } else {
        println(result)
    }
}

fun mergeASTs(name: String, vararg mergedASTs: LibraryDecl) = mergeASTs(name, mergedASTs.toList())

fun mergeASTs(name: String, mergedASTs: List<LibraryDecl>) = LibraryDecl(
        name = name,
        imports = mergedASTs.flatMap { it.imports },
        automata = mergedASTs.flatMap { it.automata },
        types = mergedASTs.flatMap { it.types },
        converters = mergedASTs.flatMap { it.converters },
        functions = mergedASTs.flatMap { it.functions }
)