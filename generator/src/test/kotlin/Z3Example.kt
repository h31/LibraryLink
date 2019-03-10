import Z3Example.Z3_lbool
import Z3Kotlin.*
import org.junit.Assert.fail
import org.junit.Test
import ru.spbstu.kspt.librarylink.*
import Z3Kotlin.Z3_context




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
            fun valueOf(value: Int): Z3_lbool? = Z3_lbool.values().find { it.i == value }
        }
    };


    @Test
    fun demorgan() {
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

        cfg                = Z3Kotlin.Z3_config();
        ctx                = cfg.Z3_mk_context();
        cfg.Z3_del_config();
        bool_sort          = ctx.Z3_mk_bool_sort();
        symbol_x           = ctx.Z3_mk_int_symbol(0);
        symbol_y           = ctx.Z3_mk_int_symbol(1);
        x                  = ctx.Z3_mk_const(symbol_x, bool_sort);
        y                  = ctx.Z3_mk_const(symbol_y, bool_sort);

        /* De Morgan - with a negation around */
        /* !(!(x && y) <-> (!x || !y)) */
        not_x              = ctx.Z3_mk_not(x);
        not_y              = ctx.Z3_mk_not(y);
        args[0]            = x;
        args[1]            = y;
        x_and_y            = ctx.Z3_mk_and(2, args);
        ls                 = ctx.Z3_mk_not(x_and_y);
        args[0]            = not_x;
        args[1]            = not_y;
        rs                 = ctx.Z3_mk_or(2, args);
        conjecture         = ctx.Z3_mk_iff(ls, rs);
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

        cfg.Z3_set_param_value("model".toList().toArrayHandle(), "true".toList().toArrayHandle())
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
    fun mk_var(ctx: Z3_context, name: ArrayHandle<Char>, ty: Z3Kotlin.Z3_sort): Z3Kotlin.Z3_ast
    {
        val s: Z3Kotlin.Z3_symbol  = ctx.Z3_mk_string_symbol(name);
        return ctx.Z3_mk_const(s, ty);
    }

    /**
    \brief Create an integer variable using the given name.
     */
    fun mk_int_var(ctx: Z3_context, name: ArrayHandle<Char>): Z3Kotlin.Z3_ast
    {
        val ty: Z3Kotlin.Z3_sort = ctx.Z3_mk_int_sort();
        return mk_var(ctx, name, ty);
    }

    /**
    \brief exit gracefully in case of error.
     */
    fun exitf(message: String)
    {
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
                print("potential model:\n%s\n".format(ctx.Z3_model_to_string(m).toString()))
            }
            Z3Example.Z3_lbool.Z3_L_TRUE -> {
                m = ctx.Z3_solver_get_model(s)
                if (m != null) ctx.Z3_model_inc_ref(m)
                val arr = ctx.Z3_model_to_string(m)
                print("sat\n%s\n".format(arr.toString()))
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
    fun find_model_example2()
    {
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

        ctx        = mk_context();
        s          = mk_solver(ctx);
        x          = mk_int_var(ctx, "x".toList().toArrayHandle());
        y          = mk_int_var(ctx, "y".toList().toArrayHandle());
        one        = mk_int(ctx, 1);
        two        = mk_int(ctx, 2);

        args[0]    = y;
        args[1]    = one;
        y_plus_one = ctx.Z3_mk_add(2, args);

        c1         = ctx.Z3_mk_lt(x, y_plus_one);
        c2         = ctx.Z3_mk_gt(x, two);

        ctx.Z3_solver_assert(s, c1);
        ctx.Z3_solver_assert(s, c2);

        print("model for: x < y + 1, x > 2\n");
        check(ctx, s, Z3_lbool.Z3_L_TRUE);

        /* assert not(x = y) */
        x_eq_y     = ctx.Z3_mk_eq(x, y);
        c3         = ctx.Z3_mk_not(x_eq_y);
        ctx.Z3_solver_assert(s,c3);

        print("model for: x < y + 1, x > 2, not(x = y)\n");
        check(ctx, s, Z3_lbool.Z3_L_TRUE);

        del_solver(ctx, s);
        ctx.Z3_del_context();
    }
}