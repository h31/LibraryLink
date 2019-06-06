#include <stdio.h>
#include <stdlib.h>
#include <z3.h>
#include <setjmp.h>

#define LOG_Z3_CALLS

#ifdef LOG_Z3_CALLS
#define LOG_MSG(msg) Z3_append_log(msg)
#else
#define LOG_MSG(msg) ((void)0)
#endif

/**
   \brief exit gracefully in case of error.
*/
void exitf(const char* message)
{
    fprintf(stderr,"BUG: %s.\n", message);
    exit(1);
}

/**
   \brief Simpler error handler.
 */
void error_handler(Z3_context c, Z3_error_code e)
{
    printf("Error code: %d\n", e);
    exitf("incorrect use of Z3");
}

/**
   \brief Create a variable using the given name and type.
*/
Z3_ast mk_var(Z3_context ctx, const char * name, Z3_sort ty)
{
    Z3_symbol   s  = Z3_mk_string_symbol(ctx, name);
    return Z3_mk_const(ctx, s, ty);
}

/**
   \brief Create a Z3 integer node using a C int.
*/
Z3_ast mk_int(Z3_context ctx, int v)
{
    Z3_sort ty = Z3_mk_int_sort(ctx);
    return Z3_mk_int(ctx, v, ty);
}

/**
   \brief Create an integer variable using the given name.
*/
Z3_ast mk_int_var(Z3_context ctx, const char * name)
{
    Z3_sort ty = Z3_mk_int_sort(ctx);
    return mk_var(ctx, name, ty);
}

Z3_solver mk_solver(Z3_context ctx)
{
    Z3_solver s = Z3_mk_solver(ctx);
    Z3_solver_inc_ref(ctx, s);
    return s;
}

void del_solver(Z3_context ctx, Z3_solver s)
{
    Z3_solver_dec_ref(ctx, s);
}

/**
   \brief Check whether the logical context is satisfiable, and compare the result with the expected result.
   If the context is satisfiable, then display the model.
*/
void check(Z3_context ctx, Z3_solver s, Z3_lbool expected_result)
{
    Z3_model m      = 0;
    Z3_lbool result = Z3_solver_check(ctx, s);
    switch (result) {
        case Z3_L_FALSE:
            printf("unsat\n");
            break;
        case Z3_L_UNDEF:
            printf("unknown\n");
            m = Z3_solver_get_model(ctx, s);
            if (m) Z3_model_inc_ref(ctx, m);
            printf("potential model:\n%s\n", Z3_model_to_string(ctx, m));
            break;
        case Z3_L_TRUE:
            m = Z3_solver_get_model(ctx, s);
            if (m) Z3_model_inc_ref(ctx, m);
            printf("sat\n%s\n", Z3_model_to_string(ctx, m));
            break;
    }
    if (result != expected_result) {
        exitf("unexpected result");
    }
    if (m) Z3_model_dec_ref(ctx, m);
}

/**
   \brief Create a logical context.
   Enable model construction. Other configuration parameters can be passed in the cfg variable.
   Also enable tracing to stderr and register custom error handler.
*/
Z3_context mk_context_custom(Z3_config cfg, Z3_error_handler err)
{
    Z3_context ctx;

    Z3_set_param_value(cfg, "model", "true");
    ctx = Z3_mk_context(cfg);
    Z3_set_error_handler(ctx, err);

    return ctx;
}

/**
   \brief Create a logical context.
   Enable model construction only.
   Also enable tracing to stderr and register standard error handler.
*/
Z3_context mk_context()
{
    Z3_config  cfg;
    Z3_context ctx;
    cfg = Z3_mk_config();
    ctx = mk_context_custom(cfg, error_handler);
    Z3_del_config(cfg);
    return ctx;
}

/**
   \brief Find a model for <tt>x < y + 1, x > 2</tt>.
   Then, assert <tt>not(x = y)</tt>, and find another model.
*/
void find_model_example2()
{
    Z3_context ctx;
    Z3_ast x, y, one, two, y_plus_one;
    Z3_ast x_eq_y;
    Z3_ast args[2];
    Z3_ast c1, c2, c3;
    Z3_solver s;

    printf("\nfind_model_example2\n");
    LOG_MSG("find_model_example2");

    ctx        = mk_context();
    s          = mk_solver(ctx);
    x          = mk_int_var(ctx, "x");
    y          = mk_int_var(ctx, "y");
    one        = mk_int(ctx, 1);
    two        = mk_int(ctx, 2);

    args[0]    = y;
    args[1]    = one;
    y_plus_one = Z3_mk_add(ctx, 2, args);

    c1         = Z3_mk_lt(ctx, x, y_plus_one);
    c2         = Z3_mk_gt(ctx, x, two);

    Z3_solver_assert(ctx, s, c1);
    Z3_solver_assert(ctx, s, c2);

    printf("model for: x < y + 1, x > 2\n");
    check(ctx, s, Z3_L_TRUE);

    /* assert not(x = y) */
    x_eq_y     = Z3_mk_eq(ctx, x, y);
    c3         = Z3_mk_not(ctx, x_eq_y);
    Z3_solver_assert(ctx, s,c3);

    printf("model for: x < y + 1, x > 2, not(x = y)\n");
    check(ctx, s, Z3_L_TRUE);

    del_solver(ctx, s);
    Z3_del_context(ctx);
}

/**
  Demonstration of how Z3 can be used to prove validity of
  De Morgan's Duality Law: {e not(x and y) <-> (not x) or ( not y) }
*/
void demorgan()
{
    Z3_config cfg;
    Z3_context ctx;
    Z3_solver s;
    Z3_sort bool_sort;
    Z3_symbol symbol_x, symbol_y;
    Z3_ast x, y, not_x, not_y, x_and_y, ls, rs, conjecture, negated_conjecture;
    Z3_ast args[2];

    printf("\nDeMorgan\n");
    LOG_MSG("DeMorgan");

    cfg                = Z3_mk_config();
    ctx                = Z3_mk_context(cfg);
    Z3_del_config(cfg);
    bool_sort          = Z3_mk_bool_sort(ctx);
    symbol_x           = Z3_mk_int_symbol(ctx, 0);
    symbol_y           = Z3_mk_int_symbol(ctx, 1);
    x                  = Z3_mk_const(ctx, symbol_x, bool_sort);
    y                  = Z3_mk_const(ctx, symbol_y, bool_sort);

    /* De Morgan - with a negation around */
    /* !(!(x && y) <-> (!x || !y)) */
    not_x              = Z3_mk_not(ctx, x);
    not_y              = Z3_mk_not(ctx, y);
    args[0]            = x;
    args[1]            = y;
    x_and_y            = Z3_mk_and(ctx, 2, args);
    ls                 = Z3_mk_not(ctx, x_and_y);
    args[0]            = not_x;
    args[1]            = not_y;
    rs                 = Z3_mk_or(ctx, 2, args);
    conjecture         = Z3_mk_iff(ctx, ls, rs);
    negated_conjecture = Z3_mk_not(ctx, conjecture);

    s = mk_solver(ctx);
    Z3_solver_assert(ctx, s, negated_conjecture);
    switch (Z3_solver_check(ctx, s)) {
        case Z3_L_FALSE:
            /* The negated conjecture was unsatisfiable, hence the conjecture is valid */
            printf("DeMorgan is valid\n");
            break;
        case Z3_L_UNDEF:
            /* Check returned undef */
            printf("Undef\n");
            break;
        case Z3_L_TRUE:
            /* The negated conjecture was satisfiable, hence the conjecture is not valid */
            printf("DeMorgan is not valid\n");
            break;
    }
    del_solver(ctx, s);
    Z3_del_context(ctx);
}


/**
   \brief Create a boolean variable using the given name.
*/
Z3_ast mk_bool_var(Z3_context ctx, const char * name)
{
    Z3_sort ty = Z3_mk_bool_sort(ctx);
    return mk_var(ctx, name, ty);
}

//static jmp_buf g_catch_buffer;
/**
   \brief Low tech exceptions.
   In high-level programming languages, an error handler can throw an exception.
*/
void throw_z3_error(Z3_context c, Z3_error_code e)
{
//    longjmp(g_catch_buffer, e);
}

/**
   \brief Create a logical context.
   Enable fine-grained proof construction.
   Enable model construction.
   Also enable tracing to stderr and register standard error handler.
*/
Z3_context mk_proof_context() {
    Z3_config cfg = Z3_mk_config();
    Z3_context ctx;
    Z3_set_param_value(cfg, "proof", "true");
    ctx = mk_context_custom(cfg, throw_z3_error);
    Z3_del_config(cfg);
    return ctx;
}

/**
   \brief exit if unreachable code was reached.
*/
void unreachable()
{
    exitf("unreachable code was reached");
}

/**
   \brief Display a symbol in the given output stream.
*/
void display_symbol(Z3_context c, FILE * out, Z3_symbol s)
{
    switch (Z3_get_symbol_kind(c, s)) {
        case Z3_INT_SYMBOL:
            fprintf(out, "#%d", Z3_get_symbol_int(c, s));
            break;
        case Z3_STRING_SYMBOL:
            fprintf(out, "%s", Z3_get_symbol_string(c, s));
            break;
        default:
            unreachable();
    }
}

/**
   \brief Display the given type.
*/
void display_sort(Z3_context c, FILE * out, Z3_sort ty)
{
    switch (Z3_get_sort_kind(c, ty)) {
        case Z3_UNINTERPRETED_SORT:
            display_symbol(c, out, Z3_get_sort_name(c, ty));
            break;
        case Z3_BOOL_SORT:
            fprintf(out, "bool");
            break;
        case Z3_INT_SORT:
            fprintf(out, "int");
            break;
        case Z3_REAL_SORT:
            fprintf(out, "real");
            break;
        case Z3_BV_SORT:
            fprintf(out, "bv%d", Z3_get_bv_sort_size(c, ty));
            break;
        case Z3_ARRAY_SORT:
            fprintf(out, "[");
            display_sort(c, out, Z3_get_array_sort_domain(c, ty));
            fprintf(out, "->");
            display_sort(c, out, Z3_get_array_sort_range(c, ty));
            fprintf(out, "]");
            break;
        case Z3_DATATYPE_SORT:
            if (Z3_get_datatype_sort_num_constructors(c, ty) != 1)
            {
                fprintf(out, "%s", Z3_sort_to_string(c,ty));
                break;
            }
            {
                unsigned num_fields = Z3_get_tuple_sort_num_fields(c, ty);
                unsigned i;
                fprintf(out, "(");
                for (i = 0; i < num_fields; i++) {
                    Z3_func_decl field = Z3_get_tuple_sort_field_decl(c, ty, i);
                    if (i > 0) {
                        fprintf(out, ", ");
                    }
                    display_sort(c, out, Z3_get_range(c, field));
                }
                fprintf(out, ")");
                break;
            }
        default:
            fprintf(out, "unknown[");
            display_symbol(c, out, Z3_get_sort_name(c, ty));
            fprintf(out, "]");
            break;
    }
}

/**
   \brief Custom ast pretty printer.
   This function demonstrates how to use the API to navigate terms.
*/
void display_ast(Z3_context c, FILE * out, Z3_ast v)
{
    switch (Z3_get_ast_kind(c, v)) {
        case Z3_NUMERAL_AST: {
            Z3_sort t;
            fprintf(out, "%s", Z3_get_numeral_string(c, v));
            t = Z3_get_sort(c, v);
            fprintf(out, ":");
            display_sort(c, out, t);
            break;
        }
        case Z3_APP_AST: {
            unsigned i;
            Z3_app app = Z3_to_app(c, v);
            unsigned num_fields = Z3_get_app_num_args(c, app);
            Z3_func_decl d = Z3_get_app_decl(c, app);
            fprintf(out, "%s", Z3_func_decl_to_string(c, d));
            if (num_fields > 0) {
                fprintf(out, "[");
                for (i = 0; i < num_fields; i++) {
                    if (i > 0) {
                        fprintf(out, ", ");
                    }
                    display_ast(c, out, Z3_get_app_arg(c, app, i));
                }
                fprintf(out, "]");
            }
            break;
        }
        case Z3_QUANTIFIER_AST: {
            fprintf(out, "quantifier");
            ;
        }
        default:
            fprintf(out, "#unknown");
    }
}

/**
   \brief Custom function interpretations pretty printer.
*/
void display_function_interpretations(Z3_context c, FILE * out, Z3_model m)
{
    unsigned num_functions, i;

    fprintf(out, "function interpretations:\n");

    num_functions = Z3_model_get_num_funcs(c, m);
    for (i = 0; i < num_functions; i++) {
        Z3_func_decl fdecl;
        Z3_symbol name;
        Z3_ast func_else;
        unsigned num_entries = 0, j;
        Z3_func_interp_opt finterp;

        fdecl = Z3_model_get_func_decl(c, m, i);
        finterp = Z3_model_get_func_interp(c, m, fdecl);
        Z3_func_interp_inc_ref(c, finterp);
        name = Z3_get_decl_name(c, fdecl);
        display_symbol(c, out, name);
        fprintf(out, " = {");
        if (finterp)
            num_entries = Z3_func_interp_get_num_entries(c, finterp);
        for (j = 0; j < num_entries; j++) {
            unsigned num_args, k;
            Z3_func_entry fentry = Z3_func_interp_get_entry(c, finterp, j);
            Z3_func_entry_inc_ref(c, fentry);
            if (j > 0) {
                fprintf(out, ", ");
            }
            num_args = Z3_func_entry_get_num_args(c, fentry);
            fprintf(out, "(");
            for (k = 0; k < num_args; k++) {
                if (k > 0) {
                    fprintf(out, ", ");
                }
                display_ast(c, out, Z3_func_entry_get_arg(c, fentry, k));
            }
            fprintf(out, "|->");
            display_ast(c, out, Z3_func_entry_get_value(c, fentry));
            fprintf(out, ")");
            Z3_func_entry_dec_ref(c, fentry);
        }
        if (num_entries > 0) {
            fprintf(out, ", ");
        }
        fprintf(out, "(else|->");
        func_else = Z3_func_interp_get_else(c, finterp);
        display_ast(c, out, func_else);
        fprintf(out, ")}\n");
        Z3_func_interp_dec_ref(c, finterp);
    }
}

/**
   \brief Custom model pretty printer.
*/
void display_model(Z3_context c, FILE * out, Z3_model m)
{
    unsigned num_constants;
    unsigned i;

    if (!m) return;

    num_constants = Z3_model_get_num_consts(c, m);
    for (i = 0; i < num_constants; i++) {
        Z3_symbol name;
        Z3_func_decl cnst = Z3_model_get_const_decl(c, m, i);
        Z3_ast a, v;
        bool ok;
        name = Z3_get_decl_name(c, cnst);
        display_symbol(c, out, name);
        fprintf(out, " = ");
        a = Z3_mk_app(c, cnst, 0, 0);
        v = a;
        ok = Z3_model_eval(c, m, a, 1, &v);
        display_ast(c, out, v);
        fprintf(out, "\n");
    }
    display_function_interpretations(c, out, m);
}

/**
   \brief Prove a theorem and extract, and print the proof.
   This example illustrates the use of #Z3_check_assumptions.
*/
void unsat_core_and_proof_example() {
    Z3_context ctx = mk_proof_context();
    Z3_solver s = mk_solver(ctx);
    Z3_ast pa = mk_bool_var(ctx, "PredA");
    Z3_ast pb = mk_bool_var(ctx, "PredB");
    Z3_ast pc = mk_bool_var(ctx, "PredC");
    Z3_ast pd = mk_bool_var(ctx, "PredD");
    Z3_ast p1 = mk_bool_var(ctx, "P1");
    Z3_ast p2 = mk_bool_var(ctx, "P2");
    Z3_ast p3 = mk_bool_var(ctx, "P3");
    Z3_ast p4 = mk_bool_var(ctx, "P4");
    Z3_ast assumptions[4] = { Z3_mk_not(ctx, p1), Z3_mk_not(ctx, p2), Z3_mk_not(ctx, p3), Z3_mk_not(ctx, p4) };
    Z3_ast args1[3] = { pa, pb, pc };
    Z3_ast f1 = Z3_mk_and(ctx, 3, args1);
    Z3_ast args2[3] = { pa, Z3_mk_not(ctx, pb), pc };
    Z3_ast f2 = Z3_mk_and(ctx, 3, args2);
    Z3_ast args3[2] = { Z3_mk_not(ctx, pa), Z3_mk_not(ctx, pc) };
    Z3_ast f3 = Z3_mk_or(ctx, 2, args3);
    Z3_ast f4 = pd;
    Z3_ast g1[2] = { f1, p1 };
    Z3_ast g2[2] = { f2, p2 };
    Z3_ast g3[2] = { f3, p3 };
    Z3_ast g4[2] = { f4, p4 };
    Z3_lbool result;
    Z3_ast proof;
    Z3_model m  = 0;
    unsigned i;
    Z3_ast_vector core;

    printf("\nunsat_core_and_proof_example\n");
    LOG_MSG("unsat_core_and_proof_example");

    Z3_solver_assert(ctx, s, Z3_mk_or(ctx, 2, g1));
    Z3_solver_assert(ctx, s, Z3_mk_or(ctx, 2, g2));
    Z3_solver_assert(ctx, s, Z3_mk_or(ctx, 2, g3));
    Z3_solver_assert(ctx, s, Z3_mk_or(ctx, 2, g4));

    result = Z3_solver_check_assumptions(ctx, s, 4, assumptions);

    switch (result) {
        case Z3_L_FALSE:
            core = Z3_solver_get_unsat_core(ctx, s);
            proof = Z3_solver_get_proof(ctx, s);
            printf("unsat\n");
            printf("proof: %s\n", Z3_ast_to_string(ctx, proof));

            printf("\ncore:\n");
            for (i = 0; i < Z3_ast_vector_size(ctx, core); ++i) {
                printf("%s\n", Z3_ast_to_string(ctx, Z3_ast_vector_get(ctx, core, i)));
            }
            printf("\n");
            break;
        case Z3_L_UNDEF:
            printf("unknown\n");
            printf("potential model:\n");
            m = Z3_solver_get_model(ctx, s);
            if (m) Z3_model_inc_ref(ctx, m);
            display_model(ctx, stdout, m);
            break;
        case Z3_L_TRUE:
            printf("sat\n");
            m = Z3_solver_get_model(ctx, s);
            if (m) Z3_model_inc_ref(ctx, m);
            display_model(ctx, stdout, m);
            break;
    }

    /* delete logical context */
    if (m) Z3_model_dec_ref(ctx, m);
    del_solver(ctx, s);
    Z3_del_context(ctx);
}






/**
   \brief Similar to #check, but uses #display_model instead of #Z3_model_to_string.
*/
void check2(Z3_context ctx, Z3_solver s, Z3_lbool expected_result)
{
    Z3_model m      = 0;
    Z3_lbool result = Z3_solver_check(ctx, s);
    switch (result) {
        case Z3_L_FALSE:
            printf("unsat\n");
            break;
        case Z3_L_UNDEF:
            printf("unknown\n");
            printf("potential model:\n");
            m = Z3_solver_get_model(ctx, s);
            if (m) Z3_model_inc_ref(ctx, m);
            display_model(ctx, stdout, m);
            break;
        case Z3_L_TRUE:
            printf("sat\n");
            m = Z3_solver_get_model(ctx, s);
            if (m) Z3_model_inc_ref(ctx, m);
            display_model(ctx, stdout, m);
            break;
    }
    if (result != expected_result) {
        exitf("unexpected result");
    }
    if (m) Z3_model_dec_ref(ctx, m);

}

/**
   \brief Show that <tt>distinct(a_0, ... , a_n)</tt> is
   unsatisfiable when \c a_i's are arrays from boolean to
   boolean and n > 4.
   This example also shows how to use the \c distinct construct.
*/
void array_example2()
{
    Z3_context ctx;
    Z3_solver s;
    Z3_sort bool_sort, array_sort;
    Z3_ast      a[5];
    Z3_ast      d;
    unsigned      i, n;

    printf("\narray_example2\n");
    LOG_MSG("array_example2");

    for (n = 2; n <= 5; n++) {
        printf("n = %d\n", n);
        ctx = mk_context();
        s = mk_solver(ctx);

        bool_sort   = Z3_mk_bool_sort(ctx);
        array_sort  = Z3_mk_array_sort(ctx, bool_sort, bool_sort);

        /* create arrays */
        for (i = 0; i < n; i++) {
            Z3_symbol s = Z3_mk_int_symbol(ctx, i);
            a[i]          = Z3_mk_const(ctx, s, array_sort);
        }

        /* assert distinct(a[0], ..., a[n]) */
        d = Z3_mk_distinct(ctx, n, a);
        printf("%s\n", Z3_ast_to_string(ctx, d));
        Z3_solver_assert(ctx, s, d);

        /* context is satisfiable if n < 5 */
        check2(ctx, s, n < 5 ? Z3_L_TRUE : Z3_L_FALSE);

        del_solver(ctx, s);
        Z3_del_context(ctx);
    }
}




int main2() {
//#ifdef LOG_Z3_CALLS
//    Z3_open_log("z3.log");
//#endif
//    display_version();
//    simple_example();
    demorgan();
//    find_model_example1();
//    find_model_example2();
//    prove_example1();
//    prove_example2();
//    push_pop_example1();
//    quantifier_example1();
//    array_example1();
//    array_example2();
//    array_example3();
//    tuple_example1();
//    bitvector_example1();
//    bitvector_example2();
//    eval_example1();
//    two_contexts_example1();
//    error_code_example1();
//    error_code_example2();
//    parser_example2();
//    parser_example3();
//    parser_example5();
//    numeral_example();
//    ite_example();
//    list_example();
//    tree_example();
//    forest_example();
//    binary_tree_example();
//    enum_example();
//    unsat_core_and_proof_example();
//    incremental_example1();
//    reference_counter_example();
//    smt2parser_example();
//    substitute_example();
//    substitute_vars_example();
//    fpa_example();
//    mk_model_example();
    return 0;
}