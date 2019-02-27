//
// Created by artyom on 28.02.19.
//

#include "handlers.h"
#include <curl/curl.h>

bool do_get() {
    CURL *curl;
    CURLcode res;

    curl = curl_easy_init();
    if (curl) {
        curl_easy_setopt(curl, CURLOPT_URL, "http://example.com");
        /* example.com is redirected, so we tell libcurl to follow redirection */
        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);

        /* Perform the request, res will get the return code */
        res = curl_easy_perform(curl);
        /* Check for errors */
        if (res != CURLE_OK) {
            fprintf(stderr, "curl_easy_perform() failed: %s\n",
                    curl_easy_strerror(res));
            return false;
        }

        /* always cleanup */
        curl_easy_cleanup(curl);
    }
    return true;
}

exchange::ChannelResponse process_request(const exchange::MethodCallRequest& request, std::unordered_map<std::string, void *>& persistence) {
//    if (request.methodname() == "curl_easy_init") {
//        CURL *curl = curl_easy_init();
//        persistence[request.assignedid()] = curl;
//        printf("curl_easy_init done");
//    } else if (request.methodname() == "curl_easy_setopt") {
//        json args = request["args"];
//        check(args.size() == 3);
//        json curl_ref = args[0];
//        check(curl_ref["type"] == "raw");
//        CURL *curl = persistence[curl_ref["value"]];
//        std::string curl_option_text = args[1]["value"];
//        CURLoption curl_option;
//        std::string curl_arg_str;
//        void* curl_arg;
//        if (curl_option_text == "CURLOPT_URL") {
//            curl_option = CURLOPT_URL;
//            curl_arg_str = args[2]["value"];
//            fmt::printf("curl_arg_str is %s \n", curl_arg_str);
//            curl_arg = (void *) curl_arg_str.c_str();
//        } else if (curl_option_text == "CURLOPT_FOLLOWLOCATION") {
//            curl_option = CURLOPT_FOLLOWLOCATION;
//            curl_arg_str = args[2]["value"];
//            fmt::printf("curl_arg_str is %s \n", curl_arg_str);
//            curl_arg = (void *) curl_arg_str.c_str();
//        } else if (curl_option_text == "CURLOPT_WRITEFUNCTION") {
//            curl_option = CURLOPT_WRITEFUNCTION;
//            write_buffer.clear(); // TODO: Multi-threading
//            curl_arg = (void *) (write_callback);
//        } else {
//            check(false);
//            break; // TODO
//        }
//
//        CURLcode res = curl_easy_setopt(curl, curl_option, curl_arg);
//        json resp;
//        resp["return_value"] = res;
//        response = resp.dump();
//    } else if (method == "curl_easy_perform") {
//        json args = request["args"];
//        check(args.size() == 1);
//        json curl_ref = args[0];
//        check(curl_ref["type"] == "raw");
//        CURL *curl = persistence[curl_ref["value"]];
//        CURLcode res = curl_easy_perform(curl);
//        json resp;
//        resp["return_value"] = res;
//        response = resp.dump();
//    } else if (method == "__read_data") {
//        json args = request["args"];
//        check(args.size() == 1);
//        json variable_name = args[0];
//        check(variable_name["type"] == "raw");
//        json resp;
//        std::string persistence_key = variable_name["value"];
//        const char* value = (const char*) persistence[persistence_key];
//        resp["return_value"] = value;
//        response = resp.dump();
//    }
}