#include <iostream>
#include <stdio.h>
#include <curl/curl.h>

#include "json.hpp"
#include <fmt/format.h>
#include <fmt/printf.h>

using json = nlohmann::json;

std::string write_buffer;

static size_t write_callback(void *contents, size_t size, size_t nmemb, void *userp) {
    write_buffer.append((char *) contents, size * nmemb);
//    ((std::string *) userp)->append((char *) contents, size * nmemb);
    return size * nmemb;
}

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

void log(const char *message) {
    printf("%s \n", message);
}

void check(bool condition) {
    if (!condition) {
        throw std::exception();
    }
}

std::unordered_map<std::string, void *> persistence;

std::unique_ptr<char[]> read_block(FILE* input, size_t length) {
    std::unique_ptr<char[]> buffer = std::make_unique<char[]>(length + 1);

    long res = fread(buffer.get(), sizeof(char), length, input);
    printf("fread res is %ld \n", res);
    printf("Raw data are %s \n", reinterpret_cast<char *>(buffer.get()));
    return buffer;
}

std::unique_ptr<char[]> read_frame(FILE* input) {
    auto lengthText = read_block(input, 4);
    size_t length = std::stoul(lengthText.get());
    printf("Len is %zu \n", length);

    auto data = read_block(input, length);
    return data;
}

std::pair<FILE*, FILE*> open_channel(const std::string &base_path, const std::string &subchannel) {
    FILE *input = fopen((base_path + "_to_receiver_" + subchannel).c_str(), "r");
    if (input == nullptr) {
        log("Input is not available");
        exit(1);
    }
    FILE *output = fopen((base_path + "_from_receiver_" + subchannel).c_str(), "w");
    if (output == nullptr) {
        log("Output is not available");
        exit(1);
    }

    return std::make_pair(input, output);
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        log("FIFO base path required, cannot proceed");
        exit(1);
    }
    std::string base_path = argv[1];
    FILE *input, *output;
    std::tie(input, output) = open_channel(base_path, "main");

//    do_get();
    while (true) {
        auto read_buffer = read_frame(input);

        auto request = json::parse(read_buffer.get());
        std::string method = request["methodName"];
        std::string assignedID = request["assignedID"];
        printf("Method is %s \n", method.c_str());
        std::string response = "{}";

        if (method == "curl_easy_init") {
            CURL *curl = curl_easy_init();
            persistence[assignedID] = curl;
            printf("curl_easy_init done");
        } else if (method == "curl_easy_setopt") {
            json args = request["args"];
            check(args.size() == 3);
            json curl_ref = args[0];
            check(curl_ref["type"] == "raw");
            CURL *curl = persistence[curl_ref["value"]];
            std::string curl_option_text = args[1]["value"];
            CURLoption curl_option;
            std::string curl_arg_str;
            void* curl_arg;
            if (curl_option_text == "CURLOPT_URL") {
                curl_option = CURLOPT_URL;
                curl_arg_str = args[2]["value"];
                fmt::printf("curl_arg_str is %s \n", curl_arg_str);
                curl_arg = (void *) curl_arg_str.c_str();
            } else if (curl_option_text == "CURLOPT_FOLLOWLOCATION") {
                curl_option = CURLOPT_FOLLOWLOCATION;
                curl_arg_str = args[2]["value"];
                fmt::printf("curl_arg_str is %s \n", curl_arg_str);
                curl_arg = (void *) curl_arg_str.c_str();
            } else if (curl_option_text == "CURLOPT_WRITEFUNCTION") {
                curl_option = CURLOPT_WRITEFUNCTION;
                write_buffer.clear(); // TODO: Multi-threading
                curl_arg = (void *) (write_callback);
            } else {
                check(false);
                break; // TODO
            }

            CURLcode res = curl_easy_setopt(curl, curl_option, curl_arg);
            json resp;
            resp["return_value"] = res;
            response = resp.dump();
        } else if (method == "curl_easy_perform") {
            json args = request["args"];
            check(args.size() == 1);
            json curl_ref = args[0];
            check(curl_ref["type"] == "raw");
            CURL *curl = persistence[curl_ref["value"]];
            CURLcode res = curl_easy_perform(curl);
            json resp;
            resp["return_value"] = res;
            response = resp.dump();
        } else if (method == "__read_data") {
            json resp;
            resp["return_value"] = write_buffer;
            response = resp.dump();
        }
        if (read_buffer == nullptr) { // TODO: Better way to stop receiver?
            break;
        }

//        size_t written = fwrite(response.c_str(), sizeof(char), 6, output); // TODO
        int written = fmt::fprintf(output, "%04d%s", response.length(), response);
        fflush(output);
        printf("Response %s \n", response.c_str());
        printf("Written %d \n", written);
    }
    return 0;
}