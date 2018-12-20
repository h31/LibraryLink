package generator

import org.stringtemplate.v4.ST
import org.stringtemplate.v4.STGroupFile
import ru.spbstu.kspt.librarymigration.parser.LibraryDecl
import ru.spbstu.kspt.librarymigration.parser.ModelParser
import java.io.InputStream

val primitiveTypes = listOf("String", "int")

fun generateST(library: LibraryDecl, st: ST) {
    val methods = mutableMapOf<String, Method>()

    val types = library.types.map { it.semanticType to it.codeType }.toMap()

    for (function in library.functions) {
        val objectId = function.staticName?.staticName ?: ""
        val property = function.properties.any { it.key == "type" && it.value == "get" }
        val static = function.staticName != null
        val args = function.args.map {
            Arg(type = checkNotNull(types[it.type]),
                    name = it.name, isReference = calcIsReference(types[it.type]!!))
        } // TODO
        val request = Request(
                methodName = function.name,
                objectID = objectId,
                args = listOf(),
                doGetReturnValue = function.returnValue != null,
                import = library.imports.firstOrNull() ?: "", // TODO
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
//                            Arg("String", "url", "StringArgument")
//                    )),
//                    Method("get", listOf(
//                            Arg("String", "url", "StringArgument"),
//                            Arg("Headers", "headers", "ReferenceArgument")
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
    var request: Request = Request("methodName", "ObjectID", listOf(), "import", true, false, true)
    var returnValue: String? = null
    var referenceReturn: Boolean = true
}

data class Arg(val type: String, val name: String, val isReference: Boolean)

fun InputStream.parseModel() = ModelParser().parse(this)

fun main(args: Array<String>) {
    val group = STGroupFile("generator/java.stg")
    val st = group.getInstanceOf("wrapperClass")
//    st.add("type", "int")
//    val wrappedClass = WrappedClass(
//            "Requests",
//            listOf(
//                    Method("get", listOf(
//                            Arg("String", "url", "StringArgument")
//                    )),
//                    Method("get", listOf(
//                            Arg("String", "url", "StringArgument"),
//                            Arg("Headers", "headers", "ReferenceArgument")
//                    ))
//            )
//    )
//    st.add("wrappedClasses", wrappedClass)
//    st.add("value", 0)
    val classLoader = Test().javaClass.classLoader
    val libraryModelAST = classLoader.getResourceAsStream("Requests.lsl").parseModel()
    val pythonModelAST = classLoader.getResourceAsStream("Python.lsl").parseModel()
    val mergedAST = mergeASTs(libraryModelAST.name, libraryModelAST, pythonModelAST)
    generateST(mergedAST, st)
    val result = st.render()
    println(result)
}

fun mergeASTs(name: String, vararg mergedASTs: LibraryDecl) = LibraryDecl(
        name = name,
        imports = mergedASTs.flatMap { it.imports },
        automata = mergedASTs.flatMap { it.automata },
        types = mergedASTs.flatMap { it.types },
        converters = mergedASTs.flatMap { it.converters },
        functions = mergedASTs.flatMap { it.functions }
)