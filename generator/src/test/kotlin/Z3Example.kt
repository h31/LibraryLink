import org.junit.Test
import ru.spbstu.kspt.librarylink.DummyRunner
import ru.spbstu.kspt.librarylink.LibraryLink
import ru.spbstu.kspt.librarylink.ProtoBufDataExchange

class Z3Example {
    init {
        LibraryLink.runner = DummyRunner(false, "/tmp/linktest")
        LibraryLink.exchange = ProtoBufDataExchange()
    }

    @Test
    fun demorgan() {
        println("DeMorgan");

        val cfg            = Z3Kotlin.Z3_config();
        val ctx            = cfg.Z3_mk_context();
        cfg.Z3_del_config();
        val bool_sort      = ctx.Z3_mk_bool_sort();
        val symbol_x       = ctx.Z3_mk_int_symbol(0);
        val symbol_y       = ctx.Z3_mk_int_symbol(1);
        val x              = ctx.Z3_mk_const(symbol_x, bool_sort);
        val y              = ctx.Z3_mk_const(symbol_y, bool_sort);

        /* De Morgan - with a negation around */
        /* !(!(x && y) <-> (!x || !y)) */
        val not_x          = ctx.Z3_mk_not(x);
        val not_y          = ctx.Z3_mk_not(y);
        args[0]            = x;
        args[1]            = y;
        val x_and_y        = ctx.Z3_mk_and(2, args);
        ls                 = Z3_mk_not(ctx, x_and_y);
        args[0]            = not_x;
        args[1]            = not_y;
        rs                 = Z3_mk_or(ctx, 2, args);
        conjecture         = Z3_mk_iff(ctx, ls, rs);
        negated_conjecture = Z3_mk_not(ctx, conjecture);
    }
}