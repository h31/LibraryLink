package generator

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.ReturnStmt
import com.github.javaparser.ast.stmt.Statement
import ru.spbstu.kspt.librarymigration.*
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

fun generateArgExpression(arg: Argument): String = "new ${arg.javaClass.simpleName}(${arg.value}, null)"

fun generateArgsStatement(args: List<Argument>): Statement {
    val initExpr: Expression
    if (args.isEmpty()) {
        initExpr = JavaParser.parseExpression("Collections.emptyList()")
    } else if (args.size == 1) {
        initExpr = JavaParser.parseExpression("Collections.singletonList(${generateArgExpression(args.single())})")
    } else {
        initExpr = JavaParser.parseExpression("Arrays.asList(${args.map { generateArgExpression(it) }.joinToString()})")
    }
    val stmt = JavaParser.parseVariableDeclarationExpr("List<Argument> args");
    stmt.variables.single().setInitializer(initExpr);
    return ExpressionStmt(stmt);
}

fun generateReturnStatement(returnValueType: String?): Statement {
    return if (returnValueType == "String" || returnValueType == "int") {
        val returnExpr = JavaParser.parseExpression<Expression>("response.getReturnValue()");
        val castExpr = CastExpr(JavaParser.parseType(returnValueType), returnExpr);
        ReturnStmt(castExpr)
    } else {
        JavaParser.parseStatement("return new $returnValueType(response.getAssignedID());") // TODO
    }
}

data class Request @JvmOverloads constructor(
        val methodName: String,
        val objectID: String = "",
        var args: List<Argument> = listOf(),
        val import: String = "",
        val isStatic: Boolean = false,
        val doGetReturnValue: Boolean = false,
        val isProperty: Boolean = false)

interface Argument {
    val type: String
    val value: Any?
    val key: String?
    val param: Param?

    fun argumentClass() = this.javaClass.getSimpleName()
}

data class StringArgument(override val value: String,
                          override val key: String? = null,
                          override val param: Param? = null) : Argument {
    override val type = "string"
}

data class NumArgument(override val value: String,
                       override val key: String? = null,
                       override val param: Param? = null) : Argument {
    override val type = "num"
}

data class RawArgument(override val value: String,
                       override val key: String? = null,
                       override val param: Param? = null) : Argument {
    override val type = "raw"
}

data class ReferenceArgument(override val value: String,
                             override val key: String? = null,
                             override val param: Param? = null) : Argument {
    override val type = "raw"
}

fun generateStatements(request: Request, returnValueType: String?): List<Statement> {
    val argsStmt = generateArgsStatement(request.args)
    val objectID = if (request.objectID == "") "getAssignedID()" else "\"${request.objectID}\""
    val statement = JavaParser.parseStatement("MethodCallRequest request = new MethodCallRequest(\"${request.methodName}\", $objectID, args, \"${request.import}\"," +
            "${request.isStatic}, ${request.doGetReturnValue}, ${request.isProperty});\n")
    val makeRequest = JavaParser.parseStatement("ProcessExchangeResponse response = exchange.makeRequest(request);")
    if (returnValueType != null) {
        val returnStatement = generateReturnStatement(returnValueType)
        return listOf(argsStmt, statement, makeRequest, returnStatement)
    } else {
        return listOf(argsStmt, statement, makeRequest)
    }
}

fun makeArgs(params: List<Param>): List<Argument> = params.withIndex().flatMap { (index, value) ->
    if (value is EntityParam && value.machine.type() == "String") {
        listOf(StringArgument("arg$index", null, value))
    } else {
        listOf(ReferenceArgument("arg$index", null, value)) // TODO
    }
}

fun makeRequest(edge: CallEdge): Request =
        Request(objectID = if (edge.isStatic) edge.machine.name.toLowerCase() else "",
                methodName = edge.methodName, args = makeArgs(edge.param), isStatic = edge.isStatic, import = "requests",
                doGetReturnValue = edge.linkedEdge != null && edge.linkedEdge?.dst?.machine?.type() == "int") // TODO!!!

fun generateConstructor(name: String): ConstructorDeclaration {
    val constructor = ConstructorDeclaration(EnumSet.of(Modifier.PUBLIC), name)
    constructor.createBody()
    constructor.body.addStatement(JavaParser.parseStatement("ReceiverRunner runner = LibraryLink.runner;\n"))
    constructor.body.addStatement(JavaParser.parseStatement("exchange = new SimpleTextProcessDataExchange(runner, runner.getRequestGenerator());"))
    return constructor
}

fun getVariableType(param: Param): String = when (param) {
    is EntityParam -> param.machine.type()
    is ConstParam -> "String" // TODO
    else -> TODO()
}

fun generate() {
    val library = readLibraryModel()

//    val originalCall = library.edges.filterIsInstance<CallEdge>().single { it.methodName == "get" }
//    originalCall.isStatic = true

    val compilationUnit = CompilationUnit()
    val wrapperClass = compilationUnit
            .addClass(library.name + "Wrapper")
            .setPublic(true)
    compilationUnit.addImport("java.util.*")
//    wrapperClass.extendedTypes = NodeList(JavaParser.parseClassOrInterfaceType("ProcessDataExchange"))
    wrapperClass.addField("ProcessDataExchange", "exchange").setPrivate(true)
    wrapperClass.members.add(generateConstructor(wrapperClass.nameAsString))
    for (type in library.machineTypes.values.filter { it.contains('.') }) {
        val actualName = getRealClassName(type)
        val clazz = ClassOrInterfaceDeclaration(EnumSet.noneOf(Modifier::class.java), false, actualName)
//        clazz.isStatic = true
        clazz.addExtendedType("Handle")
        if (type != "Requests.Requests") { // TODO: Very dirty!
            val constructor = clazz.addConstructor()
            constructor.addParameter(JavaParser.parseType("String"), "storedName")
            constructor.createBody().addStatement(JavaParser.parseStatement("super(storedName);"))
        }
        wrapperClass.addMember(clazz)
        val edges = library.edges.filter { library.machineTypes[it.src.machine] == type }
        for (edge in edges.filterIsInstance<CallEdge>()) {
            val method = clazz.addMethod(edge.methodName)
            val methodBody = method.createBody()
            val request = makeRequest(edge)
            for (arg in request.args) {
//                method.addParameter(JavaParser.parseType(getVariableType(arg.param)), arg.value.toString()) // TODO: Broken
            }
            val statements = generateStatements(request, edge.linkedEdge?.dst?.machine?.type())
            for (s in statements) {
                methodBody.addStatement(s)
            }
            if (edge.hasReturnValue) {
                val linkedEdge = edge.linkedEdge!!
                method.type = JavaParser.parseType(linkedEdge.dst.machine.type())
            }
        }
        for (edge in edges.filterIsInstance<TemplateEdge>()) {
            val method = clazz.addMethod("to" + edge.dst.machine.name)
            method.type = JavaParser.parseType(edge.dst.machine.name)
        }
    }
//    myClass.addField(Int::class.javaPrimitiveType, "A_CONSTANT", Modifier.PUBLIC, Modifier.STATIC)
//    myClass.addField(String::class.java, "name", Modifier.PRIVATE)
    val code = compilationUnit.toString()
    println(code)
}

fun getRealClassName(type: String): String {
    val actualName = type.split('.').last().replace("$", "")
    return actualName
}

fun readLibraryModel(): Library {
    val modelStream = Test().javaClass.classLoader.getResourceAsStream("Requests.lsl")
    val ast = ModelParser().parse(modelStream)
    val library = ModelParser().postprocess(ast)
    println(prettyPrinter(library.toString()))
    return library
}