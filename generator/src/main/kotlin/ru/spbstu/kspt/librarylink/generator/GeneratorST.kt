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
import ru.spbstu.kspt.librarymigration.parser.*
import java.io.File
import java.io.InputStream

var explicitSelf = false

private fun generateArgs(args: List<FunctionArgument>, types: List<TypeDecl>): List<Arg> {
    val validArgs = if (explicitSelf) args else args.filterNot { it.type.isSelf() }
    val generated = validArgs.map {
        Arg(type = types[it.type].codeType.typeName,
                name = it.name, reference = types[it.type].semanticType.isReference(), self = it.type.isSelf())
    } // TODO
    generated.filterNot { it.self }.withIndex().forEach { it.value.index = it.index }
    return generated
}

val cStyleArrayType = { type: CodeType -> CodeType("${type.typeName}*") }
val kotlinStyleArrayType = { type: CodeType -> CodeType("ArrayHandle<${type.typeName.capitalize()}>") }

val arrayTypeConverters = mapOf("kotlin" to kotlinStyleArrayType, "cpp" to cStyleArrayType)

val cStyleComplexType = mapOf(
        "[]" to "%s*",
        "*" to "%s*",
        "const" to "const %s"
)

val kotlinStyleComplexType = mapOf(
        "[]" to "ArrayHandle<%s>",
        "*" to "%s",
        "const" to "%s"
)

//val kotlinStyleComplexType = { type: ComplexSemanticType, typeDeclarations: List<TypeDecl> ->
//    when {
//        type.isArray() -> CodeType("ArrayHandle<${typeDeclarations[type.innerType].codeType.typeName.capitalize()}>")
//        type.isPointer() -> CodeType(typeDeclarations[type.innerType].codeType.typeName) // CodeType("PointerHandle<${type.innerType.typeName.capitalize()}>")
//        else -> TODO()
//    }
//}

val complexTypesConverters = mapOf("kotlin" to kotlinStyleComplexType, "cpp" to cStyleComplexType)

fun List<TypeDecl>.withSelfTypeDecl(entity: SemanticType) = this + TypeDecl(semanticType = SimpleSemanticType("self"), codeType = this[entity].codeType)

fun SemanticType.isSelf() = typeName == "self"

fun generateST(srcLibrary: LibraryDecl, st: ST, destinationLanguage: String) {
    val library = srcLibrary
            .addMissingAutomata()
            .addDefaultStates()
            .addComplexTypesDecls(checkNotNull(complexTypesConverters[destinationLanguage]))
            .generateHandlersForArrayAndPointerTypes()

    val methods = mutableMapOf<String, Method>()

    for (function in library.functions) {
        val typesWithSelf = library.types.withSelfTypeDecl(function.entity)
        val hasSelf = function.args.any { it.type.isSelf() }
        val hasReturnValue = function.returnValue != null
        val static = function.staticName != null || !hasSelf
        val objectId = if (static) function.staticName?.staticName ?: "" else "assignedID"
        val property = function.properties.any { it.key == "type" && it.value == "get" }
        val args = generateArgs(function.args, typesWithSelf)
        val request = MethodCallRequest(
                methodName = function.name,
                type = function.entity.typeName,
                objectID = objectId,
                args = listOf(),
                doGetReturnValue = hasReturnValue,
                isProperty = property,
                isStatic = static)
        val returnType = if (hasReturnValue) typesWithSelf[function.returnValue!!].codeType.typeName else null // TODO: Check
        val method = Method(name = function.name, args = args, request = request,
                returnValue = returnType, referenceReturn = function.returnValue?.isReference() ?: false, builtin = function.builtin)
        methods += method.name to method
    }

    for (automaton in library.automata) {
        if (automaton.extendable) {
            val classMethods = automaton.shifts.flatMap { it.functions }.map { checkNotNull(methods[it]) }
            if (classMethods.size == 1) { // Callback
                val srcMethod = classMethods.single()
                val method = srcMethod.copy(clazz = WrappedClass(automaton.name.typeName, listOf(), listOf(), null))
                st.add("callbacks", method)
            } else {
                TODO()
            }
        } else {
            val classMethods = automaton.shifts.filterNot { it.from == "Created" }.flatMap { it.functions }.map { checkNotNull(methods[it]) }
            val constructor = automaton.shifts.filter { it.from == "Created" }.flatMap { it.functions }.map { checkNotNull(methods[it]) }.singleOrNull()
            val builtins = methods.values.filter { it.request.type == automaton.name.typeName && it.builtin }
            val clazz = WrappedClass(
                    automaton.name.typeName,
                    classMethods,
                    builtins,
                    constructor
            )
            clazz.noExplicitConstructors = (constructor == null)
            st.add("wrappedClasses", clazz)
        }
    }
    st.add("libraryName", library.name)
}

data class WrappedClass(val name: String,
                        val methods: List<Method>,
                        val builtinMethods: List<Method>,
                        val constructor: Method?) {
    var noExplicitConstructors: Boolean = true
}

data class Method(val name: String, val args: List<Arg>,
                  val request: MethodCallRequest, var returnValue: String? = null,
                  var referenceReturn: Boolean = true, val clazz: WrappedClass? = null, val builtin: Boolean = false) // , val returnValueConstructor: String?

data class Arg(val type: String, val name: String, val reference: Boolean, val self: Boolean) {
    var index = 0
}

fun InputStream.parseModel() = ModelParser().parse(this)

class GeneratorCommand : CliktCommand() {
    val language: String by option("-l", "--language", help = "Receiver language").choice(*supportedLanguages()).required()
    val explicitSelf: Boolean by option("-s").flag()
    val output: File? by option("-o", "--output", help = "Output file").file()
    val models: List<File> by argument(help = "Models").file(exists = true).multiple(required = true).validate {
        require(it.isNotEmpty()) { "At least one model should be specified" }
    }

    override fun run() = generateWrapper(
            template = "generator/$language.stg",
            modelFiles = models,
            outputFile = output,
            explicitSelfArg = explicitSelf,
            destinationLanguage = language
    )

    private fun resourceReader(name: String) = this.javaClass.classLoader.getResourceAsStream(name).bufferedReader()

    private fun supportedLanguages() =
            resourceReader("generator/SupportedLanguages.txt").readLines().toTypedArray()
}

fun main(args: Array<String>) = GeneratorCommand().main(args)

private fun generateWrapper(template: String, modelFiles: List<File>, outputFile: File?, explicitSelfArg: Boolean, destinationLanguage: String) {
    explicitSelf = explicitSelfArg
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
    generateST(mergedAST, st, destinationLanguage)
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