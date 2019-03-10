//
// Created by artyom on 28.02.19.
//

#ifndef CURLRECEIVER_HANDLERS_H
#define CURLRECEIVER_HANDLERS_H

#include <unordered_map>
#include "exchange.pb.h"
#include <any>

extern std::atomic_uint_fast64_t assigned_id_counter;

extern std::unordered_map<std::string, void *> persistence;

exchange::ChannelResponse process_request(const exchange::MethodCallRequest& request,
                                          std::unordered_map<std::string, void *>& persistence,
                                          exchange::ChannelResponse& resp);

enum ArgumentType {
    PERSISTENCE,
    INPLACE
};

typedef std::vector<std::pair<ArgumentType, void*>> args_vector;

void do_callback(const std::string& method_name, const std::string& type, const args_vector& args);

#endif //CURLRECEIVER_HANDLERS_H
