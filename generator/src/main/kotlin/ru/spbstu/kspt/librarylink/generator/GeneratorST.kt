package ru.spbstu.kspt.librarylink.generator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import org.stringtemplate.v4.ST
import org.stringtemplate.v4.STGroupFile
import ru.spbstu.kspt.librarylink.MethodCallRequest
import ru.spbstu.kspt.librarylink.Request
import ru.spbstu.kspt.librarymigration.parser.*
import java.io.File
import java.io.InputStream

val primitiveTypes = listOf("String", "int", "Int")
var supportsOOP = false

private fun generateArgs(args: List<FunctionArgument>, types: Map<String, String>): List<Arg> {
    val validArgs = if (supportsOOP) args.filterNot { it.type == "self" } else args
    val generated = validArgs.map {
        Arg(type = checkNotNull(types[it.type]),
                name = it.name, reference = calcIsReference(types[it.type]!!), self = it.type == "self")
    } // TODO
    generated.filterNot { it.self }.withIndex().forEach { it.value.index = it.index }
    return generated
}

const val cStyleArrayType = "%s*"
const val kotlinStyleArrayType = "ArrayHandle<%s>"

fun replaceArrayType(types: Map<String, String>): Map<String, String> {
    return types.mapValues { if ("[]" in it.value) {
        "ArrayHandle<${it.value.replace("[]", "")}>"
    } else {
        it.value
    } }
}

fun patchLibraryDeclForArrays(library: LibraryDecl, types: Map<String, String>): LibraryDecl {
    val arrayTypes = getArrayTypes(library)
    val typeDecls = arrayTypes.map { type ->
        val itemType = checkNotNull(types[type.removeSuffix("[]")])
        val formatString = if (supportsOOP) kotlinStyleArrayType else cStyleArrayType
        Type(semanticType = type, codeType = formatString.format(itemType))
    }
    return library.copy(types = library.types + typeDecls)
}

fun detectArraysInTypes(library: LibraryDecl, types: Map<String, String>): List<WrappedClass> {
    val arrayTypes = getArrayTypes(library)
    val arrayClasses = mutableListOf<WrappedClass>()
    for (type in arrayTypes) {
        val itemType = checkNotNull(types[type.removeSuffix("[]")])
        val set = Method("set<$itemType>", args = listOf())
        val get = Method("get<$itemType>", args = listOf())
        val memAlloc = Method("mem_alloc<$itemType>", args = listOf())
        arrayClasses += WrappedClass(name = type, methods = listOf(),
                builtinMethods = listOf(set, get, memAlloc), constructor = null)
//        val arrayAutomaton = Automaton(name = type,
//                states = listOf(StateDecl("Created"), StateDecl("Constructed")),
//                shifts = listOf(), extendable = false)
    }
    return arrayClasses
}

private fun getArrayTypes(library: LibraryDecl): Collection<String> =
        library.functions.flatMap { it.args }.map { it.type }.filter { it.contains("[]") }.toSet()

fun generateST(srcLibrary: LibraryDecl, st: ST) {
    var types = srcLibrary.types.map { it.semanticType to it.codeType }.toMap()

    val library = patchLibraryDeclForArrays(srcLibrary, types)

    types = library.types.map { it.semanticType to it.codeType }.toMap()

    val methods = mutableMapOf<String, Method>()

    for (function in library.functions) {
        val hasSelf = function.args.any { it.type == "self" }
        val static = function.staticName != null || !hasSelf
        val objectId = if (static) function.staticName?.staticName ?: "" else "assignedID"
        val property = function.properties.any { it.key == "type" && it.value == "get" }
        val args = generateArgs(function.args, types + ("self" to function.entity))
        val request = MethodCallRequest(
                methodName = function.name,
                type = "",
                objectID = objectId,
                args = listOf(),
                doGetReturnValue = function.returnValue != null,
                isProperty = property,
                isStatic = static)
        val method = Method(function.name, args)
        method.request = request
        method.returnValue = types[function.returnValue] // TODO: Check
        method.referenceReturn = method.returnValue !in primitiveTypes
        methods += method.name to method
    }

    for (automaton in library.automata) {
        if (automaton.extendable) {
            val classMethods = automaton.shifts.flatMap { it.functions }.map { checkNotNull(methods[it]) }
            if (classMethods.size == 1) { // Callback
                val method = classMethods.single()
                method.clazz = WrappedClass(automaton.name, listOf(), listOf(), null)
                st.add("callbacks", method)
            } else {
                TODO()
            }
        } else {
            val classMethods = automaton.shifts.filterNot { it.from == "Created" }.flatMap { it.functions }.map { checkNotNull(methods[it]) }
            val constructor = automaton.shifts.filter { it.from == "Created" }.flatMap { it.functions }.map { checkNotNull(methods[it]) }.singleOrNull()
            val clazz = WrappedClass(
                    automaton.name,
                    classMethods,
                    listOf(),
                    constructor
            )
            clazz.noExplicitConstructors = (constructor == null)
            st.add("wrappedClasses", clazz)
        }
    }
    detectArraysInTypes(library, types).forEach { st.add("builtinClasses", it) }
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

data class WrappedClass(val name: String,
                        val methods: List<Method>,
                        val builtinMethods: List<Method>,
                        val constructor: Method?) {
    var noExplicitConstructors: Boolean = true
}

data class Method(val name: String, val args: List<Arg>) {
    var request: Request = MethodCallRequest("methodName", "ObjectID", "", listOf(),  true, false, true)
    var returnValue: String? = null
    var referenceReturn: Boolean = true
    var clazz: WrappedClass? = null
}

data class Arg(val type: String, val name: String, val reference: Boolean, val self: Boolean) {
    var index = 0
}

fun InputStream.parseModel() = ModelParser().parse(this)

class GeneratorCommand : CliktCommand() {
    val language: String by option("-l", "--language", help="Receiver language").choice(*supportedLanguages()).required()
    val explicitSelf: Boolean by option("-s").flag()
    val output: File? by option("-o", "--output", help = "Output file").file()
    val models: List<File> by argument(help="Models").file(exists = true).multiple(required = true).validate {
        require(it.isNotEmpty()) { "At least one model should be specified" }
    }

    override fun run() = generateWrapper(
            template = "generator/$language.stg",
            modelFiles = models,
            outputFile = output,
            explicitSelf = explicitSelf
    )

    private fun resourceReader(name: String) = this.javaClass.classLoader.getResourceAsStream(name).bufferedReader()

    private fun supportedLanguages() =
            resourceReader("generator/SupportedLanguages.txt").readLines().toTypedArray()
}

fun main(args: Array<String>) = GeneratorCommand().main(args)

private fun generateWrapper(template: String, modelFiles: List<File>, outputFile: File?, explicitSelf: Boolean) {
    supportsOOP = !explicitSelf
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