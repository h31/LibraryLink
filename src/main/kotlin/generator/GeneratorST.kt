package generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import java.beans.Beans.getInstanceOf
import org.stringtemplate.v4.ST
import org.stringtemplate.v4.STGroupFile
import org.stringtemplate.v4.STGroup
import ru.spbstu.kspt.librarymigration.CallEdge
import ru.spbstu.kspt.librarymigration.Library
import ru.spbstu.kspt.librarymigration.TemplateEdge
import ru.spbstu.kspt.librarymigration.parser.ModelParser
import java.util.*

fun generateST(library: Library, st: ST) {
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

data class WrappedClass(val name: String, val methods: List<Method>)

data class Method(val name: String, val args: List<Arg>) {
    fun isSingleArg() = args.size == 1
    var request: Request = Request("methodName", "ObjectID", listOf(), "import", true, false, true)
}

data class Arg(val type: String, val name: String, val argumentClass: String)

fun main(args: Array<String>) {
    val group = STGroupFile("generator/java.stg")
    val st = group.getInstanceOf("wrapperClass")
//    st.add("type", "int")
    st.add("libraryName", "Requests")
    val wrappedClass = WrappedClass(
            "Requests",
            listOf(
                    Method("get", listOf(
                            Arg("String", "url", "StringArgument")
                    )),
                    Method("get", listOf(
                            Arg("String", "url", "StringArgument"),
                            Arg("Headers", "headers", "ReferenceArgument")
                    ))
            )
    )
    st.add("wrappedClasses", wrappedClass)
//    st.add("value", 0)
    val library = readLibraryModel()
    generateST(library, st)
    val result = st.render() // yields "int x = 0;"
    println(result)
}