fun main(args: Array<String>) {
    doRequest()
    while (true) {
        System.gc()
        Thread.sleep(1000)
    }
}

fun doRequest() {
    val requests = Requests()
    val headers = requests.getHeaders()
    headers.update("X-Test", "Value")
    val resp = requests.get("https://api.github.com/user", headers = headers)
    println(resp.statusCode())
    println(String(resp.content()))
    println(resp.headers())
//    requests.stopPython()
}