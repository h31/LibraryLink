#include <iostream>
#include <thread>
#include <stdio.h>
#include <curl/curl.h>

#include "json.hpp"
#include <fmt/format.h>
#include <fmt/printf.h>

using json = nlohmann::json;

std::string write_buffer;

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
        log("Check failed");
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

volatile int write_callback_counter = 0;

FILE* callback_from_receiver_channel;
FILE* callback_to_receiver_channel;

static size_t write_callback(char *contents, size_t size, size_t nmemb, void *userdata) {
    const int counter = ++write_callback_counter; // TODO: Sync
    const std::string persistance_key = "write_callback_contents_" + std::to_string(counter);
    persistence[persistance_key] = contents;
    json request = {
            {"methodName", "write_callback"},
            {"objectID", ""},
            {"args", {{{"value", persistance_key},
                              {"key", "contents"},
                              {"type", "ref"}},
                                   {{"value", size},
                                           {"key", "size"},
                                           {"type", "raw"}},
                                   {{"value", nmemb},
                                           {"key", "nmemb"},
                                           {"type", "raw"}}}},
            {"import", ""},
            {"doGetReturnValue", false},
            {"assignedID", "var0"},
            {"isProperty", false},
            {"isStatic", false}
    };
    std::string request_text = request.dump();
    fmt::printf("Callback request is %s \n", request_text);
    fmt::fprintf(callback_from_receiver_channel, "%04d%s", request_text.length(), request_text);
    fflush(callback_from_receiver_channel);
//    write_buffer.append(contents, size * nmemb);
//    ((std::string *) userp)->append((char *) contents, size * nmemb);
    auto response_text = read_frame(callback_to_receiver_channel);
    json response = json::parse(response_text.get());
    size_t return_value = response["return_value"];
    return size * nmemb;
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

void process_channel(const std::string base_path, const std::string channel_name);
void open_callback_channel(const std::string &base_path);

int main(int argc, char* argv[]) {
    if (argc < 2) {
        log("FIFO base path required, cannot proceed");
        exit(1);
    }
    std::string base_path = argv[1];
    std::thread callback_thread(open_callback_channel, base_path);
    process_channel(base_path, "main");
    return 0;
}

void open_callback_channel(const std::string &base_path) {
    std::tie(callback_to_receiver_channel, callback_from_receiver_channel) = open_channel(base_path, "callback"); // TODO: may block
}

void process_channel(const std::string base_path, const std::string channel_name) {
    FILE *input, *output;

    std::tie(input, output) = open_channel(base_path, channel_name);

//    do_get();
    while (true) {
        auto read_buffer = read_frame(input);

        auto request = json::parse(read_buffer.get());

        if (request.find("register_channel") != request.end()) {
            printf("register_channel! \n");
            std::string new_channel_name = request["register_channel"];
            std::thread new_thread(process_channel, base_path, new_channel_name);
            new_thread.detach();
            continue;
        }

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
            json args = request["args"];
            check(args.size() == 1);
            json variable_name = args[0];
            check(variable_name["type"] == "raw");
            json resp;
            std::string persistence_key = variable_name["value"];
            const char* value = (const char*) persistence[persistence_key];
            resp["return_value"] = value;
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
}