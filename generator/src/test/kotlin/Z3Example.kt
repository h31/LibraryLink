import org.junit.Test

class Z3Example {
    val z3 = Z3Kotlin()

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
    }
}