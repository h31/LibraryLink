import ru.spbstu.kspt.librarylink.*;

import java.util.*;

public class Z3Kotlin {
    private val exchange = LibraryLink.exchange;


    class Z3_config : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super() {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_mk_config",
                                            objectID = "",
                                            type = "Z3_config",
                                            args = args,
                                            isStatic = true,
                                            doGetReturnValue = true,
                                            isProperty = false);
            exchange.makeRequest(request).bindTo(this);
        }
        public fun Z3_mk_context(): Z3_context {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_mk_context",
                                            objectID = assignedID,
                                            type = "Z3_config",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_context())

        }

        public fun Z3_set_param_value(param_id: ArrayHandle<Char>, param_value: ArrayHandle<Char>) {
            val args = listOf<Argument>(Argument(param_id, "param_id"), Argument(param_value, "param_value"));
            val request = MethodCallRequest(methodName = "Z3_set_param_value",
                                            objectID = assignedID,
                                            type = "Z3_config",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_del_config() {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_del_config",
                                            objectID = assignedID,
                                            type = "Z3_config",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }
    }

    class Z3_context : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super();

        public fun Z3_mk_bool_sort(): Z3_sort {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_mk_bool_sort",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_sort())

        }

        public fun Z3_mk_int_symbol(i: Int): Z3_symbol {
            val args = listOf<Argument>(Argument(i, "i"));
            val request = MethodCallRequest(methodName = "Z3_mk_int_symbol",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_symbol())

        }

        public fun Z3_mk_const(s: Z3_symbol, ty: Z3_sort): Z3_ast {
            val args = listOf<Argument>(Argument(s, "s"), Argument(ty, "ty"));
            val request = MethodCallRequest(methodName = "Z3_mk_const",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_and(num_args: Int, args: ArrayHandle<Z3_ast>): Z3_ast {
            val args = listOf<Argument>(Argument(num_args, "num_args"), Argument(args, "args"));
            val request = MethodCallRequest(methodName = "Z3_mk_and",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_or(num_args: Int, args: ArrayHandle<Z3_ast>): Z3_ast {
            val args = listOf<Argument>(Argument(num_args, "num_args"), Argument(args, "args"));
            val request = MethodCallRequest(methodName = "Z3_mk_or",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_not(a: Z3_ast): Z3_ast {
            val args = listOf<Argument>(Argument(a, "a"));
            val request = MethodCallRequest(methodName = "Z3_mk_not",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_iff(t1: Z3_ast, t2: Z3_ast): Z3_ast {
            val args = listOf<Argument>(Argument(t1, "t1"), Argument(t2, "t2"));
            val request = MethodCallRequest(methodName = "Z3_mk_iff",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_solver(): Z3_solver {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_mk_solver",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_solver())

        }

        public fun Z3_solver_inc_ref(s: Z3_solver) {
            val args = listOf<Argument>(Argument(s, "s"));
            val request = MethodCallRequest(methodName = "Z3_solver_inc_ref",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_solver_dec_ref(s: Z3_solver) {
            val args = listOf<Argument>(Argument(s, "s"));
            val request = MethodCallRequest(methodName = "Z3_solver_dec_ref",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_solver_assert(s: Z3_solver, a: Z3_ast) {
            val args = listOf<Argument>(Argument(s, "s"), Argument(a, "a"));
            val request = MethodCallRequest(methodName = "Z3_solver_assert",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_solver_check(s: Z3_solver): Int {
            val args = listOf<Argument>(Argument(s, "s"));
            val request = MethodCallRequest(methodName = "Z3_solver_check",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).asInstanceOf()

        }

        public fun Z3_mk_string_symbol(s: ArrayHandle<Char>): Z3_symbol {
            val args = listOf<Argument>(Argument(s, "s"));
            val request = MethodCallRequest(methodName = "Z3_mk_string_symbol",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_symbol())

        }

        public fun Z3_mk_int_sort(): Z3_sort {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_mk_int_sort",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_sort())

        }

        public fun Z3_solver_get_model(s: Z3_solver): Z3_model {
            val args = listOf<Argument>(Argument(s, "s"));
            val request = MethodCallRequest(methodName = "Z3_solver_get_model",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_model())

        }

        public fun Z3_model_inc_ref(m: Z3_model) {
            val args = listOf<Argument>(Argument(m, "m"));
            val request = MethodCallRequest(methodName = "Z3_model_inc_ref",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_model_to_string(m: Z3_model): ArrayHandle<Char> {
            val args = listOf<Argument>(Argument(m, "m"));
            val request = MethodCallRequest(methodName = "Z3_model_to_string",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(CharArrayHandle())

        }

        public fun Z3_model_dec_ref(m: Z3_model) {
            val args = listOf<Argument>(Argument(m, "m"));
            val request = MethodCallRequest(methodName = "Z3_model_dec_ref",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_mk_add(num_args: Int, args: ArrayHandle<Z3_ast>): Z3_ast {
            val args = listOf<Argument>(Argument(num_args, "num_args"), Argument(args, "args"));
            val request = MethodCallRequest(methodName = "Z3_mk_add",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_lt(t1: Z3_ast, t2: Z3_ast): Z3_ast {
            val args = listOf<Argument>(Argument(t1, "t1"), Argument(t2, "t2"));
            val request = MethodCallRequest(methodName = "Z3_mk_lt",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_gt(t1: Z3_ast, t2: Z3_ast): Z3_ast {
            val args = listOf<Argument>(Argument(t1, "t1"), Argument(t2, "t2"));
            val request = MethodCallRequest(methodName = "Z3_mk_gt",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_eq(l: Z3_ast, r: Z3_ast): Z3_ast {
            val args = listOf<Argument>(Argument(l, "l"), Argument(r, "r"));
            val request = MethodCallRequest(methodName = "Z3_mk_eq",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_mk_int(v: Int, ty: Z3_sort): Z3_ast {
            val args = listOf<Argument>(Argument(v, "v"), Argument(ty, "ty"));
            val request = MethodCallRequest(methodName = "Z3_mk_int",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = true,
                                            isProperty = false);
            return exchange.makeRequest(request).bindTo(Z3_ast())

        }

        public fun Z3_set_error_handler(h: Z3_error_handler) {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_set_error_handler",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }

        public fun Z3_del_context() {
            val args = listOf<Argument>();
            val request = MethodCallRequest(methodName = "Z3_del_context",
                                            objectID = assignedID,
                                            type = "Z3_context",
                                            args = args,
                                            isStatic = false,
                                            doGetReturnValue = false,
                                            isProperty = false);
            exchange.makeRequest(request)

        }
    }

    class Z3_sort : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super();

    }

    class Z3_symbol : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super();

    }

    class Z3_ast : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super();

    }

    class Z3_solver : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super();

    }

    class Z3_model : HandleAutoGenerate {
        private val exchange = LibraryLink.exchange;
        constructor() : super();

    }

    abstract class Z3_error_handler : DelayedAssignmentHandle {
        private val exchange = LibraryLink.exchange;

        abstract fun invoke(c: Z3_context, e: Int)

        constructor() : super() {
            exchange.registerCallback("invoke", "Z3_error_handler") { req, _ ->
                this.invoke(Z3_context(), 0)
            }
        }

    }
}