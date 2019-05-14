#include <utility>
#include <unordered_map>
#include "exchange.pb.h"

#include <z3.h>
#include "handlers.h"
#include "helpers.h"

void librarylink_Z3_mk_context(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_config* cfg = (Z3_config*) persistence[request.object_id()];

    Z3_context return_value = Z3_mk_context(*cfg);
    Z3_context* ptr = new Z3_context;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_set_param_value(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_config* cfg = (Z3_config*) persistence[request.object_id()];

    char** param_id = (char**) persistence[request.arg(0).value().string_value()];

    char** param_value = (char**) persistence[request.arg(1).value().string_value()];

Z3_set_param_value(*cfg, *param_id, *param_value);
}

void librarylink_Z3_del_config(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_config* cfg = (Z3_config*) persistence[request.object_id()];

Z3_del_config(*cfg);
}
void librarylink_Z3_mk_config(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_config return_value = Z3_mk_config();
    Z3_config* ptr = new Z3_config;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_bool_sort(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_sort return_value = Z3_mk_bool_sort(*cfg);
    Z3_sort* ptr = new Z3_sort;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_int_symbol(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    int i = request.arg(0).value().int_value();

    Z3_symbol return_value = Z3_mk_int_symbol(*cfg, i);
    Z3_symbol* ptr = new Z3_symbol;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_const(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_symbol* s = (Z3_symbol*) persistence[request.arg(0).value().string_value()];

    Z3_sort* ty = (Z3_sort*) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_const(*cfg, *s, *ty);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_and(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    int num_args = request.arg(0).value().int_value();

    Z3_ast** args = (Z3_ast**) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_and(*cfg, num_args, *args);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_or(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    int num_args = request.arg(0).value().int_value();

    Z3_ast** args = (Z3_ast**) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_or(*cfg, num_args, *args);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_not(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_ast* a = (Z3_ast*) persistence[request.arg(0).value().string_value()];

    Z3_ast return_value = Z3_mk_not(*cfg, *a);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_iff(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_ast* t1 = (Z3_ast*) persistence[request.arg(0).value().string_value()];

    Z3_ast* t2 = (Z3_ast*) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_iff(*cfg, *t1, *t2);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_solver(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_solver return_value = Z3_mk_solver(*cfg);
    Z3_solver* ptr = new Z3_solver;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_solver_inc_ref(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

Z3_solver_inc_ref(*cfg, *s);
}

void librarylink_Z3_solver_dec_ref(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

Z3_solver_dec_ref(*cfg, *s);
}

void librarylink_Z3_solver_assert(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

    Z3_ast* a = (Z3_ast*) persistence[request.arg(1).value().string_value()];

Z3_solver_assert(*cfg, *s, *a);
}

void librarylink_Z3_solver_check(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

    int return_value = Z3_solver_check(*cfg, *s);
    resp.mutable_return_value()->set_int_value(return_value);
}

void librarylink_Z3_solver_check_assumptions(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

    int num_assumptions = request.arg(1).value().int_value();

    Z3_ast** assumptions = (Z3_ast**) persistence[request.arg(2).value().string_value()];

    int return_value = Z3_solver_check_assumptions(*c, *s, num_assumptions, *assumptions);
    resp.mutable_return_value()->set_int_value(return_value);
}

void librarylink_Z3_solver_get_unsat_core(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

    Z3_ast_vector return_value = Z3_solver_get_unsat_core(*c, *s);
    Z3_ast_vector* ptr = new Z3_ast_vector;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_string_symbol(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    char** s = (char**) persistence[request.arg(0).value().string_value()];

    Z3_symbol return_value = Z3_mk_string_symbol(*cfg, *s);
    Z3_symbol* ptr = new Z3_symbol;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_int_sort(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_sort return_value = Z3_mk_int_sort(*c);
    Z3_sort* ptr = new Z3_sort;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_solver_get_model(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_solver* s = (Z3_solver*) persistence[request.arg(0).value().string_value()];

    Z3_model return_value = Z3_solver_get_model(*c, *s);
    Z3_model* ptr = new Z3_model;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_model_inc_ref(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_model* m = (Z3_model*) persistence[request.arg(0).value().string_value()];

Z3_model_inc_ref(*c, *m);
}

void librarylink_Z3_model_to_string(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_model* m = (Z3_model*) persistence[request.arg(0).value().string_value()];

    const char* return_value = Z3_model_to_string(*c, *m);
    const char** ptr = new const char*;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_model_dec_ref(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_model* m = (Z3_model*) persistence[request.arg(0).value().string_value()];

Z3_model_dec_ref(*c, *m);
}

void librarylink_Z3_mk_add(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    int num_args = request.arg(0).value().int_value();

    Z3_ast** args = (Z3_ast**) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_add(*c, num_args, *args);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_lt(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_ast* t1 = (Z3_ast*) persistence[request.arg(0).value().string_value()];

    Z3_ast* t2 = (Z3_ast*) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_lt(*c, *t1, *t2);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_gt(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_ast* t1 = (Z3_ast*) persistence[request.arg(0).value().string_value()];

    Z3_ast* t2 = (Z3_ast*) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_gt(*c, *t1, *t2);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_eq(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_ast* l = (Z3_ast*) persistence[request.arg(0).value().string_value()];

    Z3_ast* r = (Z3_ast*) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_eq(*c, *l, *r);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_int(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    int v = request.arg(0).value().int_value();

    Z3_sort* ty = (Z3_sort*) persistence[request.arg(1).value().string_value()];

    Z3_ast return_value = Z3_mk_int(*c, v, *ty);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_set_error_handler(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_error_handler* h = (Z3_error_handler*) persistence[request.arg(0).value().string_value()];

Z3_set_error_handler(*c, *h);
}

void librarylink_Z3_get_symbol_kind(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_symbol* s = (Z3_symbol*) persistence[request.arg(0).value().string_value()];

    int return_value = Z3_get_symbol_kind(*cfg, *s);
    resp.mutable_return_value()->set_int_value(return_value);
}

void librarylink_Z3_get_symbol_int(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_symbol* s = (Z3_symbol*) persistence[request.arg(0).value().string_value()];

    int return_value = Z3_get_symbol_int(*cfg, *s);
    resp.mutable_return_value()->set_int_value(return_value);
}

void librarylink_Z3_get_symbol_string(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

    Z3_symbol* s = (Z3_symbol*) persistence[request.arg(0).value().string_value()];

    const char* return_value = Z3_get_symbol_string(*cfg, *s);
    const char** ptr = new const char*;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_model_get_num_consts(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_model* m = (Z3_model*) persistence[request.arg(0).value().string_value()];

    int return_value = Z3_model_get_num_consts(*c, *m);
    resp.mutable_return_value()->set_int_value(return_value);
}

void librarylink_Z3_model_get_const_decl(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_model* m = (Z3_model*) persistence[request.arg(0).value().string_value()];

    int i = request.arg(1).value().int_value();

    Z3_func_decl return_value = Z3_model_get_const_decl(*c, *m, i);
    Z3_func_decl* ptr = new Z3_func_decl;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_get_decl_name(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_func_decl* d = (Z3_func_decl*) persistence[request.arg(0).value().string_value()];

    Z3_symbol return_value = Z3_get_decl_name(*c, *d);
    Z3_symbol* ptr = new Z3_symbol;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_mk_app(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* c = (Z3_context*) persistence[request.object_id()];

    Z3_func_decl* d = (Z3_func_decl*) persistence[request.arg(0).value().string_value()];

    int num_args = request.arg(1).value().int_value();

    Z3_ast** args = (Z3_ast**) persistence[request.arg(2).value().string_value()];

    Z3_ast return_value = Z3_mk_app(*c, *d, num_args, *args);
    Z3_ast* ptr = new Z3_ast;
    *ptr = std::move(return_value);
    persistence[resp.assigned_id()] = ptr;
}

void librarylink_Z3_del_context(const exchange::MethodCallRequest& request,
                                     std::unordered_map<std::string, void *>& persistence,
                                     exchange::ChannelResponse& resp) {
    Z3_context* cfg = (Z3_context*) persistence[request.object_id()];

Z3_del_context(*cfg);
}



































exchange::ChannelResponse process_request(const exchange::MethodCallRequest& request,
                                          std::unordered_map<std::string, void *>& persistence,
                                          exchange::ChannelResponse& resp) {
    if (request.type() == "Z3_config" && request.methodname() == "Z3_mk_context") {
        librarylink_Z3_mk_context(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_config" && request.methodname() == "Z3_set_param_value") {
        librarylink_Z3_set_param_value(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_config" && request.methodname() == "Z3_del_config") {
        librarylink_Z3_del_config(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_config" && request.methodname() == "Z3_mk_config") {
        librarylink_Z3_mk_config(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_bool_sort") {
        librarylink_Z3_mk_bool_sort(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_int_symbol") {
        librarylink_Z3_mk_int_symbol(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_const") {
        librarylink_Z3_mk_const(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_and") {
        librarylink_Z3_mk_and(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_or") {
        librarylink_Z3_mk_or(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_not") {
        librarylink_Z3_mk_not(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_iff") {
        librarylink_Z3_mk_iff(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_solver") {
        librarylink_Z3_mk_solver(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_inc_ref") {
        librarylink_Z3_solver_inc_ref(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_dec_ref") {
        librarylink_Z3_solver_dec_ref(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_assert") {
        librarylink_Z3_solver_assert(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_check") {
        librarylink_Z3_solver_check(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_check_assumptions") {
        librarylink_Z3_solver_check_assumptions(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_get_unsat_core") {
        librarylink_Z3_solver_get_unsat_core(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_string_symbol") {
        librarylink_Z3_mk_string_symbol(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_int_sort") {
        librarylink_Z3_mk_int_sort(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_solver_get_model") {
        librarylink_Z3_solver_get_model(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_model_inc_ref") {
        librarylink_Z3_model_inc_ref(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_model_to_string") {
        librarylink_Z3_model_to_string(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_model_dec_ref") {
        librarylink_Z3_model_dec_ref(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_add") {
        librarylink_Z3_mk_add(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_lt") {
        librarylink_Z3_mk_lt(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_gt") {
        librarylink_Z3_mk_gt(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_eq") {
        librarylink_Z3_mk_eq(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_int") {
        librarylink_Z3_mk_int(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_set_error_handler") {
        librarylink_Z3_set_error_handler(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_get_symbol_kind") {
        librarylink_Z3_get_symbol_kind(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_get_symbol_int") {
        librarylink_Z3_get_symbol_int(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_get_symbol_string") {
        librarylink_Z3_get_symbol_string(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_model_get_num_consts") {
        librarylink_Z3_model_get_num_consts(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_model_get_const_decl") {
        librarylink_Z3_model_get_const_decl(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_get_decl_name") {
        librarylink_Z3_get_decl_name(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_mk_app") {
        librarylink_Z3_mk_app(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_context" && request.methodname() == "Z3_del_context") {
        librarylink_Z3_del_context(request, persistence, resp);
        return resp;
    }








    if (request.type() == "Char[]" && request.methodname() == "set<char>") {
        librarylink_set<char>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Char[]" && request.methodname() == "get<char>") {
        librarylink_get<char>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Char[]" && request.methodname() == "mem_alloc<char>") {
        librarylink_mem_alloc<char>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Char[]" && request.methodname() == "set<char>") {
        librarylink_set<char>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Char[]" && request.methodname() == "get<char>") {
        librarylink_get<char>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Char[]" && request.methodname() == "mem_alloc<char>") {
        librarylink_mem_alloc<char>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Z3_ast[]" && request.methodname() == "set<Z3_ast>") {
        librarylink_set<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "get<Z3_ast>") {
        librarylink_get<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "mem_alloc<Z3_ast>") {
        librarylink_mem_alloc<Z3_ast>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Z3_ast[]" && request.methodname() == "set<Z3_ast>") {
        librarylink_set<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "get<Z3_ast>") {
        librarylink_get<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "mem_alloc<Z3_ast>") {
        librarylink_mem_alloc<Z3_ast>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Z3_ast[]" && request.methodname() == "set<Z3_ast>") {
        librarylink_set<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "get<Z3_ast>") {
        librarylink_get<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "mem_alloc<Z3_ast>") {
        librarylink_mem_alloc<Z3_ast>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Z3_ast[]" && request.methodname() == "set<Z3_ast>") {
        librarylink_set<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "get<Z3_ast>") {
        librarylink_get<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "mem_alloc<Z3_ast>") {
        librarylink_mem_alloc<Z3_ast>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Char[]" && request.methodname() == "set<char>") {
        librarylink_set<char>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Char[]" && request.methodname() == "get<char>") {
        librarylink_get<char>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Char[]" && request.methodname() == "mem_alloc<char>") {
        librarylink_mem_alloc<char>(request, persistence, resp);
        return resp;
    }

    if (request.type() == "Z3_ast[]" && request.methodname() == "set<Z3_ast>") {
        librarylink_set<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "get<Z3_ast>") {
        librarylink_get<Z3_ast>(request, persistence, resp);
        return resp;
    }
    if (request.type() == "Z3_ast[]" && request.methodname() == "mem_alloc<Z3_ast>") {
        librarylink_mem_alloc<Z3_ast>(request, persistence, resp);
        return resp;
    }



    if (request.methodname() == "strlen") {
        librarylink_strlen(request, persistence, resp);
        return resp;
    }
    printf("No handler found\n");
    throw "error";
}