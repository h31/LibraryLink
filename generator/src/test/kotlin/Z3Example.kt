import org.junit.Test
import ru.spbstu.kspt.librarylink.*

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
        val result = Z3_lbool.valueOf(ctx.Z3_solver_check(s))
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
}