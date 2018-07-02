import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.Reader
import java.io.Writer
import java.util.*
import org.slf4j.LoggerFactory


fun main(args: Array<String>) {
    val requests = Requests()
    val resp = requests.get("https://api.github.com/user")
    println(resp.statusCode())
    println(String(resp.content()))
    println(resp.headers())
    requests.stopPython()
}