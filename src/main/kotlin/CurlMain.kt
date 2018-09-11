fun main(args: Array<String>) {
    doCurl()
}

fun doCurl() {
    LibraryLink.runner = DummyRunner(isMultiThreaded = true, channelPrefix = "/tmp/curl")
    val curlWrapper = CurlWrapper()
    val curl = curlWrapper.curl_easy_init()
    curlWrapper.curl_easy_setopt(curl, "CURLOPT_URL", "http://example.com")
    curlWrapper.curl_easy_setopt(curl, "CURLOPT_WRITEFUNCTION", "")
    curlWrapper.curl_easy_perform(curl)
}