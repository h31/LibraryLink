//
// Created by artyom on 28.02.19.
//

#ifndef CURLRECEIVER_HANDLERS_H
#define CURLRECEIVER_HANDLERS_H

#include <unordered_map>
#include "exchange.pb.h"

exchange::ChannelResponse process_request(const exchange::MethodCallRequest& request, std::unordered_map<std::string, void *>& persistence);

#endif //CURLRECEIVER_HANDLERS_H
