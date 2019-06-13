package ru.spbstu.kspt.librarylink;

import Z3Kotlin.*
import org.junit.Assert.fail
import org.junit.Test
import Z3Kotlin.Z3_context
import Z3Kotlin.Z3_config
import java.io.Writer
import java.lang.IllegalArgumentException
import kotlin.system.measureTimeMillis


class Z3Example {
    init {
        LibraryLink.runner = DummyRunner(false, "/tmp/linktest")
        LibraryLink.exchange = ProtoBufDataExchange()
    }

    fun mk_solver(ctx: Z3Kotlin.Z3_context): Z3Kotlin.Z3_solver {
        val s: Z3Kotlin.Z3_solver = ctx.Z3_mk_solver();
        ctx.Z3_solver_inc_ref(s);
        return s;
    }

    fun del_solver(ctx: Z3Kotlin.Z3_context, s: Z3Kotlin.Z3_solver) {
        ctx.Z3_solver_dec_ref(s);
    }

    enum class Z3_lbool(val i: Int) {
        Z3_L_FALSE(-1),
        Z3_L_UNDEF(0),
        Z3_L_TRUE(1);

        companion object {
            fun valueOf(value: Int): Z3_lbool = Z3_lbool.values().find { it.i == value }
                    ?: throw NoSuchElementException()
        }
    };

    enum class Z3_symbol_kind(val i: Int) {
        Z3_INT_SYMBOL(0),
        Z3_STRING_SYMBOL(1);

        companion object {
            fun valueOf(value: Int): Z3_symbol_kind = Z3_symbol_kind.values().find { it.i == value }
                    ?: throw NoSuchElementException()
        }
    };

    interface Indexed {
        val i: Int
    }

    enum class Z3_ast_kind(override val i: Int) : Indexed {
        Z3_NUMERAL_AST(0),
        Z3_APP_AST(1),
        Z3_VAR_AST(2),
        Z3_QUANTIFIER_AST(3),
        Z3_SORT_AST(4),
        Z3_FUNC_DECL_AST(5),
        Z3_UNKNOWN_AST(1000);

        companion object {
            fun valueOf(value: Int): Z3_ast_kind = Z3_ast_kind.values().find { it.i == value }
                    ?: throw NoSuchElementException()
        }
    };

    enum class Z3_sort_kind(val i: Int) {
        Z3_UNINTERPRETED_SORT(0),
        Z3_BOOL_SORT(1),
        Z3_INT_SORT(2),
        Z3_REAL_SORT(3),
        Z3_BV_SORT(4),
        Z3_ARRAY_SORT(5),
        Z3_DATATYPE_SORT(6),
        Z3_RELATION_SORT(7),
        Z3_FINITE_DOMAIN_SORT(8),
        Z3_FLOATING_POINT_SORT(9),
        Z3_ROUNDING_MODE_SORT(10),
        Z3_SEQ_SORT(11),
        Z3_RE_SORT(12),
        Z3_UNKNOWN_SORT(1000);

        companion object {
            fun valueOf(value: Int): Z3_sort_kind = Z3_sort_kind.values().find { it.i == value }
                    ?: throw NoSuchElementException()
        }
    };

//    inline fun <T> enumValueOf(value: Int): T where T : Enum<Any>, T : Indexed = T::class.java.getMethod("values")

    fun demorganStripped(): String {
        val cfg: Z3Kotlin.Z3_config;
        val ctx: Z3Kotlin.Z3_context;
        val s: Z3Kotlin.Z3_solver;
        val bool_sort: Z3Kotlin.Z3_sort;
        val symbol_x: Z3Kotlin.Z3_symbol;
        val symbol_y: Z3Kotlin.Z3_symbol;
        val x: Z3Kotlin.Z3_ast;
        val y: Z3Kotlin.Z3_ast
        val not_x: Z3Kotlin.Z3_ast
        val not_y: Z3Kotlin.Z3_ast
        val x_and_y: Z3Kotlin.Z3_ast
        val ls: Z3Kotlin.Z3_ast
        val rs: Z3Kotlin.Z3_ast
        val conjecture: Z3Kotlin.Z3_ast
        val negated_conjecture: Z3Kotlin.Z3_ast;
        val args = makeArray<Z3Kotlin.Z3_ast>(2);

//        println("DeMorgan");

        cfg = Z3Kotlin.Z3_config();
        ctx = cfg.Z3_mk_context();
        cfg.Z3_del_config();
        bool_sort = ctx.Z3_mk_bool_sort();
        symbol_x = ctx.Z3_mk_int_symbol(0);
        symbol_y = ctx.Z3_mk_int_symbol(1);
        x = ctx.Z3_mk_const(symbol_x, bool_sort);
        y = ctx.Z3_mk_const(symbol_y, bool_sort);

        /* De Morgan - with a negation around */
        /* !(!(x && y) <-> (!x || !y)) */
        not_x = ctx.Z3_mk_not(x);
        not_y = ctx.Z3_mk_not(y);
        args[0] = x;
        args[1] = y;
        x_and_y = ctx.Z3_mk_and(2, args);
        ls = ctx.Z3_mk_not(x_and_y);
        args[0] = not_x;
        args[1] = not_y;
        rs = ctx.Z3_mk_or(2, args);
        conjecture = ctx.Z3_mk_iff(ls, rs);
        negated_conjecture = ctx.Z3_mk_not(conjecture);

        s = mk_solver(ctx);
        ctx.Z3_solver_assert(s, negated_conjecture);
        val result = Z3_lbool.valueOf(ctx.Z3_solver_check(s)) // TODO
//        println("Result is $result")
        when (result) {
            Z3_lbool.Z3_L_FALSE ->
                /* The negated conjecture was unsatisfiable, hence the conjecture is valid */
                return "DeMorgan is valid\n";
            Z3_lbool.Z3_L_UNDEF ->
                /* Check returned undef */
                return "Undef\n";
            Z3_lbool.Z3_L_TRUE ->
                /* The negated conjecture was satisfiable, hence the conjecture is not valid */
                return "DeMorgan is not valid\n";
        }
//        del_solver(ctx, s);
//        ctx.Z3_del_context();
    }

    @Test
    fun demorgan() {
//        Thread.sleep(30 * 1000)

        val time = measureTimeMillis {

            val cfg: Z3Kotlin.Z3_config;
            val ctx: Z3Kotlin.Z3_context;
            val s: Z3Kotlin.Z3_solver;
            val bool_sort: Z3Kotlin.Z3_sort;
            val symbol_x: Z3Kotlin.Z3_symbol;
            val symbol_y: Z3Kotlin.Z3_symbol;
            val x: Z3Kotlin.Z3_ast;
            val y: Z3Kotlin.Z3_ast
            val not_x: Z3Kotlin.Z3_ast
            val not_y: Z3Kotlin.Z3_ast
            val x_and_y: Z3Kotlin.Z3_ast
            val ls: Z3Kotlin.Z3_ast
            val rs: Z3Kotlin.Z3_ast
            val conjecture: Z3Kotlin.Z3_ast
            val negated_conjecture: Z3Kotlin.Z3_ast;
            val args = makeArray<Z3Kotlin.Z3_ast>(2);

            println("DeMorgan");

            cfg = Z3Kotlin.Z3_config();
        ctx = cfg.Z3_mk_context();
        cfg.Z3_del_config();
        bool_sort = ctx.Z3_mk_bool_sort();
        symbol_x = ctx.Z3_mk_int_symbol(0);
        symbol_y = ctx.Z3_mk_int_symbol(1);
        x = ctx.Z3_mk_const(symbol_x, bool_sort);
        y = ctx.Z3_mk_const(symbol_y, bool_sort);

        /* De Morgan - with a negation around */
        /* !(!(x && y) <-> (!x || !y)) */
        not_x = ctx.Z3_mk_not(x);
        not_y = ctx.Z3_mk_not(y);
        args[0] = x;
        args[1] = y;
        x_and_y = ctx.Z3_mk_and(2, args);
        ls = ctx.Z3_mk_not(x_and_y);
        args[0] = not_x;
        args[1] = not_y;
        rs = ctx.Z3_mk_or(2, args);
        conjecture = ctx.Z3_mk_iff(ls, rs);
        negated_conjecture = ctx.Z3_mk_not(conjecture);

        s = mk_solver(ctx);
        ctx.Z3_solver_assert(s, negated_conjecture);
        val result = Z3_lbool.valueOf(ctx.Z3_solver_check(s)) // TODO
        println("Result is $result")
        when (result) {
            Z3_lbool.Z3_L_FALSE ->
                /* The negated conjecture was unsatisfiable, hence the conjecture is valid */
                print("DeMorgan is valid\n");
            Z3_lbool.Z3_L_UNDEF ->
                /* Check returned undef */
                print("Undef\n");
            Z3_lbool.Z3_L_TRUE ->
                /* The negated conjecture was satisfiable, hence the conjecture is not valid */
                print("DeMorgan is not valid\n");
        }
        del_solver(ctx, s);
        ctx.Z3_del_context();
        }

        println(time)
    }

    class ErrorHandler : Z3_error_handler() {
        /**
         * \brief Simpler error handler.
         */
        override fun invoke(c: Z3_context, e: Int) {
            print("Error code: %d\n".format(e))
            exitf("incorrect use of Z3")
        }

        /**
        \brief exit gracefully in case of error.
         */
        fun exitf(message: String) // TODO
        {
            fail("BUG: %s.\n".format(message)); // TODO: Exit?
        }
    }

    /**
     * \brief Create a logical context.
     * Enable model construction. Other configuration parameters can be passed in the cfg variable.
     * Also enable tracing to stderr and register custom error handler.
     */
    fun mk_context_custom(cfg: Z3_config, err: Z3_error_handler): Z3_context {
        val ctx: Z3_context

        cfg.Z3_set_param_value("model".toArrayHandle(), "true".toArrayHandle())
        ctx = cfg.Z3_mk_context()
//        ctx.Z3_set_error_handler(err)

        return ctx
    }

    /**
     * \brief Create a logical context.
     * Enable model construction only.
     * Also enable tracing to stderr and register standard error handler.
     */
    fun mk_context(): Z3_context {
        val cfg: Z3_config
        val ctx: Z3_context
        cfg = Z3_config()
        ctx = mk_context_custom(cfg, ErrorHandler())
        cfg.Z3_del_config()
        return ctx
    }

    /**
     * \brief Create a Z3 integer node using a C int.
     */
    fun mk_int(ctx: Z3_context, v: Int): Z3_ast {
        val ty = ctx.Z3_mk_int_sort()
        return ctx.Z3_mk_int(v, ty)
    }

    /**
    \brief Create a variable using the given name and type.
     */
    fun mk_var(ctx: Z3_context, name: ArrayHandle<Char>, ty: Z3Kotlin.Z3_sort): Z3Kotlin.Z3_ast {
        val s: Z3Kotlin.Z3_symbol = ctx.Z3_mk_string_symbol(name);
        return ctx.Z3_mk_const(s, ty);
    }

    /**
    \brief Create an integer variable using the given name.
     */
    fun mk_int_var(ctx: Z3_context, name: ArrayHandle<Char>): Z3Kotlin.Z3_ast {
        val ty: Z3Kotlin.Z3_sort = ctx.Z3_mk_int_sort();
        return mk_var(ctx, name, ty);
    }

    /**
    \brief exit gracefully in case of error.
     */
    fun exitf(message: String) {
        fail("BUG: %s.\n".format(message)); // TODO: Exit?
    }

    /**
     * \brief Check whether the logical context is satisfiable, and compare the result with the expected result.
     * If the context is satisfiable, then display the model.
     */
    fun check(ctx: Z3_context, s: Z3_solver, expected_result: Z3_lbool) {
        var m: Z3_model? = null
        val result = ctx.Z3_solver_check(s)
        val lbool = Z3_lbool.valueOf(result) // TODO
        when (lbool) {
            Z3Example.Z3_lbool.Z3_L_FALSE -> print("unsat\n")
            Z3Example.Z3_lbool.Z3_L_UNDEF -> {
                print("unknown\n")
                m = ctx.Z3_solver_get_model(s)
                if (m != null) ctx.Z3_model_inc_ref(m)
                print("potential model:\n%s\n".format(ctx.Z3_model_to_string(m).toStringOfSize()))
            }
            Z3Example.Z3_lbool.Z3_L_TRUE -> {
                m = ctx.Z3_solver_get_model(s)
                if (m != null) ctx.Z3_model_inc_ref(m)
                val arr = ctx.Z3_model_to_string(m)
                print("sat\n%s\n".format(arr.toStringOfSize()))
            }
        }
        if (lbool !== expected_result) {
            exitf("unexpected result")
        }
        if (m != null) ctx.Z3_model_dec_ref(m)
    }

    /**
    \brief Find a model for <tt>x < y + 1, x > 2</tt>.
    Then, assert <tt>not(x = y)</tt>, and find another model.
     */
    @Test
    fun find_model_example2() {
        val ctx: Z3Kotlin.Z3_context;
        val x: Z3Kotlin.Z3_ast;
        val y: Z3Kotlin.Z3_ast
        val one: Z3Kotlin.Z3_ast
        val two: Z3Kotlin.Z3_ast
        val y_plus_one: Z3Kotlin.Z3_ast;
        val x_eq_y: Z3Kotlin.Z3_ast;
        val args: ArrayHandle<Z3Kotlin.Z3_ast> = makeArray(2)
        val c1: Z3Kotlin.Z3_ast
        val c2: Z3Kotlin.Z3_ast
        val c3: Z3Kotlin.Z3_ast;
        val s: Z3Kotlin.Z3_solver;

        print("\nfind_model_example2\n");

        ctx = mk_context();
        s = mk_solver(ctx);
        x = mk_int_var(ctx, "x".toArrayHandle());
        y = mk_int_var(ctx, "y".toArrayHandle());
        one = mk_int(ctx, 1);
        two = mk_int(ctx, 2);

        args[0] = y;
        args[1] = one;
        y_plus_one = ctx.Z3_mk_add(2, args);

        c1 = ctx.Z3_mk_lt(x, y_plus_one);
        c2 = ctx.Z3_mk_gt(x, two);

        ctx.Z3_solver_assert(s, c1);
        ctx.Z3_solver_assert(s, c2);

        print("model for: x < y + 1, x > 2\n");
        check(ctx, s, Z3_lbool.Z3_L_TRUE);

        /* assert not(x = y) */
        x_eq_y = ctx.Z3_mk_eq(x, y);
        c3 = ctx.Z3_mk_not(x_eq_y);
        ctx.Z3_solver_assert(s, c3);

        print("model for: x < y + 1, x > 2, not(x = y)\n");
        check(ctx, s, Z3_lbool.Z3_L_TRUE);

        del_solver(ctx, s);
        ctx.Z3_del_context();
    }

    /**
     * \brief Create a logical context.
     * Enable fine-grained proof construction.
     * Enable model construction.
     * Also enable tracing to stderr and register standard error handler.
     */
    fun mk_proof_context(): Z3_context {
        val cfg = Z3Kotlin.Z3_config()
        val ctx: Z3_context
        cfg.Z3_set_param_value("proof".toArrayHandle(), "true".toArrayHandle())
        ctx = mk_context_custom(cfg, ErrorHandler())
        cfg.Z3_del_config()
        return ctx
    }

    /**
    \brief Create a boolean variable using the given name.
     */
    fun mk_bool_var(ctx: Z3_context, name: String): Z3_ast {
        val ty: Z3_sort = ctx.Z3_mk_bool_sort();
        return mk_var(ctx, name.toArrayHandle(), ty);
    }

    /**
    \brief Display a symbol in the given output stream.
     */
    fun display_symbol(c: Z3_context, output: Writer, s: Z3_symbol) {
        val kind = c.Z3_get_symbol_kind(s)
        when (Z3_symbol_kind.valueOf(kind)) {
            Z3_symbol_kind.Z3_INT_SYMBOL -> output.write("#%d".format(c.Z3_get_symbol_int(s)))
            Z3_symbol_kind.Z3_STRING_SYMBOL -> output.write("%s".format(c.Z3_get_symbol_string(s)))
            else -> throw IllegalArgumentException()
        }
    }

//    /**
//    \brief Display the given type.
//     */
//    fun display_sort(c: Z3_context, output: Writer, ty: Z3_sort) {
//        when (Z3_sort_kind.valueOf(c.Z3_get_sort_kind(ty))) {
//            Z3_sort_kind.Z3_UNINTERPRETED_SORT -> display_symbol(c, output, c.Z3_get_sort_name(ty))
//            Z3_sort_kind.Z3_BOOL_SORT -> output.write("bool")
//            Z3_sort_kind.Z3_INT_SORT -> output.write("int")
//            Z3_sort_kind.Z3_REAL_SORT -> output.write( "real")
//            Z3_sort_kind.Z3_BV_SORT -> output.write( "bv" + c.Z3_get_bv_sort_size(ty))
//            Z3_sort_kind.Z3_ARRAY_SORT -> {
//                output.write( "[")
//                display_sort(c, output, c.Z3_get_array_sort_domain(ty))
//                output.write( "->")
//                display_sort(c, output, c.Z3_get_array_sort_range(ty))
//                output.write( "]")
//            }
//            Z3_sort_kind.Z3_DATATYPE_SORT -> {
//                if (Z3_get_datatype_sort_num_constructors(c, ty) !== 1) {
//                    output.write(Z3_sort_to_string(c, ty))
//                }
//                else {
//                    val num_fields = Z3_get_tuple_sort_num_fields(c, ty)
//                    val i: unsigned
//                    output.write( "(")
//                    i = 0
//                    while (i < num_fields) {
//                        val field = Z3_get_tuple_sort_field_decl(c, ty, i)
//                        if (i > 0) {
//                            output.write( ", ")
//                        }
//                        display_sort(c, out, Z3_get_range(c, field))
//                        i++
//                    }
//                    output.write( ")")
//                }
//                output.write( "unknown[")
//                display_symbol(c, out, Z3_get_sort_name(c, ty))
//                output.write( "]")
//            }
//            else -> {
//                output.write( "unknown[")
//                display_symbol(c, out, Z3_get_sort_name(c, ty))
//                output.write( "]")
//            }
//        }
//    }

//    /**
//    \brief Custom ast pretty printer.
//    This function demonstrates how to use the API to navigate terms.
//     */
//    fun display_ast(c: Z3_context, output: Writer, v: Z3_ast) {
//        when (Z3_ast_kind.valueOf(c.Z3_get_ast_kind(v))) {
//            Z3_ast_kind.Z3_NUMERAL_AST -> {
//                val t: Z3_sort
//                output.write("%s".format(c.Z3_get_numeral_string(v)))
//                t = c.Z3_get_sort(v)
//                output.write(":")
//                display_sort(c, output, t)
//            }
//            Z3_ast_kind.Z3_APP_AST -> {
//                val i: unsigned
//                val app = Z3_to_app(c, v)
//                val num_fields = Z3_get_app_num_args(c, app)
//                val d = Z3_get_app_decl(c, app)
//                output.write(Z3_func_decl_to_string(c, d))
//                if (num_fields > 0) {
//                    output.write( "[")
//                    i = 0
//                    while (i < num_fields) {
//                        if (i > 0) {
//                            output.write( ", ")
//                        }
//                        display_ast(c, output, Z3_get_app_arg(c, app, i))
//                        i++
//                    }
//                    output.write( "]")
//                }
//            }
//            Z3_ast_kind.Z3_QUANTIFIER_AST -> {
//                run({ output.write( "quantifier") })
//                output.write( "#unknown")
//            }
//            else -> output.write( "#unknown")
//        }
//    }

//    /**
//    \brief Custom model pretty printer.
//     */
//    fun display_model(c: Z3_context, output: Writer, m: Z3_model?) {
//        val num_constants: Int
//        var i: Int
//        if (m == null) return
//        num_constants = c.Z3_model_get_num_consts(m)
//        i = 0
//        while (i < num_constants) {
//            val name: Z3_symbol
//            val cnst = c.Z3_model_get_const_decl(m, i)
//            val a: Z3_ast
//            val v: Z3_ast
//            val ok: Boolean
//            name = c.Z3_get_decl_name(cnst)
//            display_symbol(c, output, name)
//            output.write(" = ")
//            a = c.Z3_mk_app(cnst, 0, listOf<Z3_ast>(mk_bool_var(c, "PredAsadfafgdsgfsg")).toArrayHandle())
//            v = a
//            ok = c.Z3_model_eval(m, a, true, v)
//            display_ast(c, out, v) TODO
//                    output.write("\n")
//            i++
//        }
////        display_function_interpretations(c, out, m) TODO
//    }

//    /**
//    \brief Prove a theorem and extract, and print the proof.
//    This example illustrates the use of #Z3_check_assumptions.
//     */
//    @Test
//    fun unsat_core_and_proof_example() {
//        val ctx: Z3_context = mk_proof_context();
//        val s: Z3_solver = mk_solver(ctx);
//        val pa: Z3_ast = mk_bool_var(ctx, "PredA");
//        val pb: Z3_ast = mk_bool_var(ctx, "PredB");
//        val pc: Z3_ast = mk_bool_var(ctx, "PredC");
//        val pd: Z3_ast = mk_bool_var(ctx, "PredD");
//        val p1: Z3_ast = mk_bool_var(ctx, "P1");
//        val p2: Z3_ast = mk_bool_var(ctx, "P2");
//        val p3: Z3_ast = mk_bool_var(ctx, "P3");
//        val p4: Z3_ast = mk_bool_var(ctx, "P4");
//        val assumptions = listOf<Z3_ast>(ctx.Z3_mk_not(p1), ctx.Z3_mk_not(p2), ctx.Z3_mk_not(p3), ctx.Z3_mk_not(p4));
//        val args1 = listOf<Z3_ast>(pa, pb, pc);
//        val f1: Z3_ast = ctx.Z3_mk_and(3, args1.toArrayHandle());
//        val args2 = listOf<Z3_ast>(pa, ctx.Z3_mk_not(pb), pc);
//        val f2: Z3_ast = ctx.Z3_mk_and(3, args2.toArrayHandle());
//        val args3 = listOf<Z3_ast>(ctx.Z3_mk_not(pa), ctx.Z3_mk_not(pc));
//        val f3: Z3_ast = ctx.Z3_mk_or(2, args3.toArrayHandle());
//        val f4: Z3_ast = pd;
//        val g1 = listOf<Z3_ast>(f1, p1);
//        val g2 = listOf<Z3_ast>(f2, p2);
//        val g3 = listOf<Z3_ast>(f3, p3);
//        val g4 = listOf<Z3_ast>(f4, p4);
//        val result: Z3_lbool;
//        val proof: Z3_ast;
//        val m: Z3_model?;
////        val i: Int;
//        val core: Z3Kotlin.Z3_ast_vector;
//
//        print("\nunsat_core_and_proof_example\n");
////        LOG_MSG("unsat_core_and_proof_example");
//
////        ctx.Z3_solver_assert(s, ctx.Z3_mk_or(2, g1.toArrayHandle()));
////        ctx.Z3_solver_assert(s, ctx.Z3_mk_or(2, g2.toArrayHandle()));
////        ctx.Z3_solver_assert(s, ctx.Z3_mk_or(2, g3.toArrayHandle()));
////        ctx.Z3_solver_assert(s, ctx.Z3_mk_or(2, g4.toArrayHandle()));
//
//        result = Z3_lbool.valueOf(ctx.Z3_solver_check_assumptions(s, 4, assumptions.toArrayHandle()));
//
//        when (result) {
//            Z3_lbool.Z3_L_FALSE -> {
//                TODO()
////                core = ctx.Z3_solver_get_unsat_core(s);
////                proof = ctx.Z3_solver_get_proof(s);
////                print("unsat\n");
////                print("proof: %s\n".format(ctx.Z3_ast_to_string(proof)));
////
////                print("\ncore:\n");
////                for (i in 0 until ctx.Z3_ast_vector_size(core)) {
////                    print("%s\n".format(Z3_ast_to_string(ctx, Z3_ast_vector_get(ctx, core, i))));
////                }
////                printf("\n");
//            }
//            Z3_lbool.Z3_L_UNDEF -> {
//                print("unknown\n");
//                print("potential model:\n");
//                m = ctx.Z3_solver_get_model(s);
//                if (m != null) ctx.Z3_model_inc_ref(m);
//                display_model(ctx, System.out.writer(), m);
//            }
//            Z3_lbool.Z3_L_TRUE -> {
//                print("sat\n");
//                m = ctx.Z3_solver_get_model(s);
//                if (m != null) ctx.Z3_model_inc_ref(m);
//                display_model(ctx, System.out.writer(), m);
//            }
//        }
//
//        /* delete logical context */
//        if (m != null) ctx.Z3_model_dec_ref(m);
//        del_solver(ctx, s);
//        ctx.Z3_del_context();
//    }

//    /**
//    \brief Similar to #check, but uses #display_model instead of #Z3_model_to_string.
//     */
//    fun array_example2() {
//        val ctx: Z3_context
//        val s: Z3_solver
//        val bool_sort: Z3_sort
//        val array_sort: Z3_sort
//        val a: Array<Z3_ast>
//        val d: Z3_ast
//        val i: Int
//        val n: Int
//        print("\narray_example2\n")
////        LOG_MSG("array_example2")
//        n = 2
//        while (n <= 5) {
//            printf("n = %d\n", n)
//            ctx = mk_context()
//            s = mk_solver(ctx)
//            bool_sort = Z3_mk_bool_sort(ctx)
//            array_sort = Z3_mk_array_sort(ctx, bool_sort, bool_sort)
//            /* create arrays */
//            i = 0
//            while (i < n) {
//                val s = Z3_mk_int_symbol(ctx, i)
//                a[i] = Z3_mk_const(ctx, s, array_sort)
//                i++
//            }
//            /* assert distinct(a[0], ..., a[n]) */
//            d = Z3_mk_distinct(ctx, n, a)
//            printf("%s\n", Z3_ast_to_string(ctx, d))
//            Z3_solver_assert(ctx, s, d)
//            /* context is satisfiable if n < 5 */
//            check2(ctx, s, if (n < 5) Z3_L_TRUE else Z3_L_FALSE)
//            del_solver(ctx, s)
//            Z3_del_context(ctx)
//            n++
//        }
//    }
}