import ru.spbstu.kspt.librarylink.DummyRunner
import ru.spbstu.kspt.librarylink.Jennifer
import ru.spbstu.kspt.librarylink.LibraryLink

fun main(args: Array<String>) {
    LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
    val jen = Jennifer()

    val f = jen.NewFile("main")
    f.ToGroup().Func().Id("main").Params().Block()
    val code = f.GoString()
    println(code)
}
