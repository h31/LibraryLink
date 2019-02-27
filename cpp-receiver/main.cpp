#include <iostream>
#include <thread>
#include <stdio.h>
#include <unistd.h>
#include <utility>

#include <fmt/format.h>
#include <fmt/printf.h>
#include <unordered_map>

#include <sys/socket.h>
#include <sys/un.h>
#include <string.h>

#include "exchange.pb.h"

#include "handlers.h"

std::string write_buffer;

typedef std::tuple<uint32_t, uint32_t, std::unique_ptr<uint8_t>> channel_request;

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

template <typename T> std::unique_ptr<T> read_block(int fd, size_t length = 1) {
    std::unique_ptr<T> buffer = std::make_unique<T>(length);

    long res = read(fd, buffer.get(), sizeof(T)*length);
    printf("fread res is %ld \n", res);
    return buffer;
}

channel_request read_frame(int fd) {
    auto length = read_block<uint32_t>(fd);
    printf("Len is %u \n", *length);

    auto tag = read_block<uint32_t>(fd);
    printf("Tag is %u \n", *tag);

    auto data = read_block<uint8_t>(fd, *length);
    return std::make_tuple(*length, *tag, std::move(data));
}

void write_frame(int fd, uint32_t tag, const std::string& message) {
    auto size = static_cast<uint32_t>(message.size());
    write(fd, &size, sizeof(size));
    write(fd, &tag, sizeof(tag));
    write(fd, message.data(), size);
}

volatile int write_callback_counter = 0;

FILE* callback_from_receiver_channel;
FILE* callback_to_receiver_channel;

//static size_t write_callback(char *contents, size_t size, size_t nmemb, void *userdata) {
//    const int counter = ++write_callback_counter; // TODO: Sync
//    const std::string persistance_key = "write_callback_contents_" + std::to_string(counter);
//    persistence[persistance_key] = contents;
//    json request = {
//            {"methodName", "write_callback"},
//            {"objectID", ""},
//            {"args", {{{"value", persistance_key},
//                              {"key", "contents"},
//                              {"type", "ref"}},
//                                   {{"value", size},
//                                           {"key", "size"},
//                                           {"type", "raw"}},
//                                   {{"value", nmemb},
//                                           {"key", "nmemb"},
//                                           {"type", "raw"}}}},
//            {"import", ""},
//            {"doGetReturnValue", false},
//            {"assignedID", "var0"},
//            {"isProperty", false},
//            {"isStatic", false}
//    };
//    std::string request_text = request.dump();
//    fmt::printf("Callback request is %s \n", request_text);
//    fmt::fprintf(callback_from_receiver_channel, "%04d%s", request_text.length(), request_text);
//    fflush(callback_from_receiver_channel);
////    write_buffer.append(contents, size * nmemb);
////    ((std::string *) userp)->append((char *) contents, size * nmemb);
//    auto response_text = read_frame(callback_to_receiver_channel);
//    json response = json::parse(response_text.get());
//    size_t return_value = response["return_value"];
//    return size * nmemb;
//}

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

void process_channel(int fd);
void open_callback_channel(const std::string &base_path);
void unix_socket_server(std::string socket_path);

int main(int argc, char* argv[]) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;

    if (argc < 2) {
        log("FIFO base path required, cannot proceed");
        exit(1);
    }
    std::string base_path = argv[1];
    std::thread callback_thread(open_callback_channel, base_path);
    unix_socket_server(base_path);
    return 0;
}

void unix_socket_server(std::string socket_path) {
    struct sockaddr_un addr = {};
    int fd,client;

    if ( (fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        perror("socket error");
        exit(-1);
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, socket_path.data(), sizeof(addr.sun_path)-1);
//        unlink(socket_path);

    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        perror("bind error");
        exit(-1);
    }

    if (listen(fd, 5) == -1) {
        perror("listen error");
        exit(-1);
    }

    while (true) {
        if ( (client = accept(fd, nullptr, nullptr)) == -1) {
            perror("accept error");
            break;
        }

        std::thread process_thread(process_channel, client);

//        while ( (rc=read(cl,buf,sizeof(buf))) > 0) {
//            printf("read %u bytes: %.*s\n", rc, rc, buf);
//        }
//        if (rc == -1) {
//            perror("read");
//            exit(-1);
//        }
//        else if (rc == 0) {
//            printf("EOF\n");
//            close(cl);
//        }
    }
}

void open_callback_channel(const std::string &base_path) {
    std::tie(callback_to_receiver_channel, callback_from_receiver_channel) = open_channel(base_path, "callback"); // TODO: may block
}

void process_channel(int fd) {
    std::string response_bytes;

    while (true) {
        uint32_t length, tag;
        std::unique_ptr<uint8_t> request_bytes;

        std::tie(length, tag, request_bytes) = read_frame(fd);

        if (tag == 8) { // TODO: Enum
            break;
        }

        exchange::Request rq;
        rq.ParseFromArray(request_bytes.get(), length);
        exchange::ChannelResponse response;

        switch (rq.request_case()) {
            case rq.kMethodCall:
                auto request = rq.method_call();

                printf("Method is %s \n", request.methodname().c_str());

                response = process_request(request, persistence);
        }


//        if (request.find("register_channel") != request.end()) {
//            printf("register_channel! \n");
//            std::string new_channel_name = request["register_channel"];
//            std::thread new_thread(process_channel, base_path, new_channel_name);
//            new_thread.detach();
//            continue;
//        }

//        size_t written = fwrite(response.c_str(), sizeof(char), 6, output); // TODO
        int written = fmt::printf("%s\n", response.DebugString());
        response.SerializeToString(&response_bytes);
        write_frame(fd, 1, response_bytes);
    }
}