fun main(args: Array<String>) {
    val requests = Requests()
    val resp = requests.get("https://api.github.com/user")
    println(resp.statusCode())
    println(String(resp.content()))
    println(resp.headers())
    requests.stopPython()
}