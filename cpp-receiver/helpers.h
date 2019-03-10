//
// Created by artyom on 05.03.19.
//

#ifndef LIBRARYLINKRECEIVER_HELPERS_H
#define LIBRARYLINKRECEIVER_HELPERS_H

#include "handlers.h"

template <typename T> void librarylink_mem_alloc(const exchange::MethodCallRequest& request,
                                                 std::unordered_map<std::string, void *>& persistence,
                                                 exchange::ChannelResponse& resp);

template <typename T> void librarylink_set(const exchange::MethodCallRequest& request,
                                           std::unordered_map<std::string, void *>& persistence,
                                           exchange::ChannelResponse& resp);

template <typename T> void librarylink_get(const exchange::MethodCallRequest& request,
                                           std::unordered_map<std::string, void *>& persistence,
                                           exchange::ChannelResponse& resp);

template<typename T>
void
librarylink_mem_alloc(const exchange::MethodCallRequest& request,
                      std::unordered_map<std::string, void *>& persistence,
                      exchange::ChannelResponse& resp) {
    int64_t size = request.arg(0).value().int_value();
//    T* array = new T[size];
    T* array = (T*) calloc(size, sizeof(T));

    T** ptr = new T*;
    *ptr = std::move(array);
    persistence[resp.assigned_id()] = ptr;
}

template<typename T>
void librarylink_set(const exchange::MethodCallRequest& request,
                     std::unordered_map<std::string, void *>& persistence,
                     exchange::ChannelResponse& resp) {
    T** array = (T**) (persistence[request.object_id()]);
    int64_t pos = request.arg(0).value().int_value();
    T* value = (T*) persistence[request.arg(1).value().string_value()];

    (*array)[pos] = *value;
}

template<>
void librarylink_set<char>(const exchange::MethodCallRequest& request,
                     std::unordered_map<std::string, void *>& persistence,
                     exchange::ChannelResponse& resp) {
    char** array = (char**) (persistence[request.object_id()]);
    int64_t pos = request.arg(0).value().int_value();
    char value = static_cast<char>(request.arg(1).value().int_value());

    (*array)[pos] = value;
}

template<typename T>
void librarylink_get(const exchange::MethodCallRequest& request,
                     std::unordered_map<std::string, void *>& persistence,
                     exchange::ChannelResponse& resp) {
    T** array = (T**) (persistence[request.object_id()]);
    int64_t pos = request.arg(0).value().int_value();
    T return_value = (*array)[pos];
    T* ptr = new T;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

template<>
void librarylink_get<char>(const exchange::MethodCallRequest &request,
                           std::unordered_map<std::string, void *> &persistence,
                           exchange::ChannelResponse &resp) {
    char** array = (char**) (persistence[request.object_id()]);
    int64_t pos = request.arg(0).value().int_value();
    char return_value = (*array)[pos];
    resp.mutable_return_value()->set_int_value(return_value);
}

void librarylink_strlen(const exchange::MethodCallRequest &request,
                        std::unordered_map<std::string, void *> &persistence,
                        exchange::ChannelResponse &resp) {
    char** array = (char**) (persistence[request.object_id()]);
    int return_value = static_cast<int>(strlen(*array));
    resp.mutable_return_value()->set_int_value(return_value);
}

#endif //LIBRARYLINKRECEIVER_HELPERS_H
