fun main(args: Array<String>) {
    doRequest()
    while (true) {
        System.gc()
        Thread.sleep(1000)
    }
}

fun doRequest() {
    LibraryLink.runner = Python3Runner("src/main/python/main.py", "/tmp/wrapperfifo")
    val requests = Requests()
    val headers = requests.getHeaders()
    headers.update("X-Test", "Value")
    val resp = requests.get("https://api.github.com/user", headers)
    println("Status code is ${resp.statusCode()}")
    println("Content is ${String(resp.content())}")
    println("Headers are ${resp.headers()}")
//    requests.stopPython()
}

fun doCurl() {
    LibraryLink.runner = DummyRunner(isMultiThreaded = false, channelPrefix = "/tmp/curl")
    val curlWrapper = CurlWrapper()
    val curl = curlWrapper.curl_easy_init()
    curlWrapper.curl_easy_setopt(curl, "CURLOPT_URL", "http://example.com")
}