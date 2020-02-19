library Z3 {
 imports {
   z3.h;
 }

 includes {
   C;
 }

 types {
   Z3_config (Z3_config);
   Z3_context (Z3_context);
   Z3_solver (Z3_solver);
   Z3_sort (Z3_sort);
   Z3_symbol (Z3_symbol);
   Z3_ast (Z3_ast);
   Z3_solver (Z3_solver);
   Z3_lbool (Z3_lbool);
   Z3_model (Z3_model);
   Z3_error_handler (Z3_error_handler);
   Z3_func_decl (Z3_func_decl);
   Z3_ast_vector (Z3_ast_vector);
 }

 converters {
   Z3_ast_size <- sizeof(Z3_ast);
 }

 automaton Z3_config {
   state Created, Constructed, Closed;
   shift Created -> Constructed (Z3_mk_config);
   shift Constructed -> self (Z3_mk_context, Z3_set_param_value);
   shift Constructed -> Closed (Z3_del_config);
 }

 fun Z3_config.Z3_mk_config(): Z3_config;
 fun Z3_config.Z3_mk_context(cfg: self): Z3_context;
 fun Z3_config.Z3_del_config(cfg: self);
 fun Z3_config.Z3_set_param_value(cfg: self, param_id: Char[], param_value: Char[]);

 automaton Z3_context {
   state Constructed;
   shift Constructed -> self (Z3_mk_bool_sort, Z3_get_range, Z3_get_tuple_sort_field_decl);
   shift Constructed -> self (Z3_mk_int_symbol, Z3_mk_const, Z3_mk_and, Z3_mk_or, Z3_mk_not, Z3_mk_iff, Z3_sort_to_string);
   shift Constructed -> self (Z3_mk_solver, Z3_solver_inc_ref, Z3_solver_dec_ref, Z3_get_datatype_sort_num_constructors);
   shift Constructed -> self (Z3_solver_assert, Z3_solver_check, Z3_solver_check_assumptions, Z3_solver_get_unsat_core, Z3_get_tuple_sort_num_fields);
   shift Constructed -> self (Z3_mk_string_symbol, Z3_mk_int_sort, Z3_solver_get_model, Z3_model_inc_ref, Z3_model_to_string, Z3_model_dec_ref, Z3_mk_add, Z3_mk_lt, Z3_mk_gt, Z3_mk_eq, Z3_mk_int, Z3_set_error_handler, Z3_get_symbol_kind, Z3_get_symbol_int, Z3_get_symbol_string, Z3_model_get_num_consts, Z3_model_get_const_decl, Z3_get_decl_name, Z3_mk_app, Z3_model_eval, Z3_get_ast_kind, Z3_get_numeral_string, Z3_get_sort, Z3_get_sort_kind, Z3_get_sort_name, Z3_get_bv_sort_size, Z3_get_array_sort_domain, Z3_get_array_sort_range);
   shift Constructed -> Closed (Z3_del_context);
 }

 fun Z3_context.Z3_mk_bool_sort(cfg: self): Z3_sort;
 fun Z3_context.Z3_mk_int_symbol(cfg: self, i: Int): Z3_symbol;

 automaton Z3_sort {
   state Constructed;
 }

 automaton Z3_symbol {
   state Created, Constructed;
 }

 automaton Z3_ast {
   state Created, Constructed;
 }
 
 automaton Z3_solver {
   state Created, Constructed;
 }
 
 automaton Z3_model {
   state Created, Constructed;
 }
 
 automaton Z3_func_decl {
   state Created, Constructed;
 }
 
 automaton Z3_ast_vector {
   state Created, Constructed;
 }

 fun Z3_context.Z3_mk_const(cfg: self, s: Z3_symbol, ty: Z3_sort): Z3_ast;
 fun Z3_context.Z3_mk_int(c: self, v: Int, ty: Z3_sort): Z3_ast;
 
 fun Z3_context.Z3_mk_not(cfg: self, a: Z3_ast): Z3_ast;
 fun Z3_context.Z3_mk_iff(cfg: self, t1: Z3_ast, t2: Z3_ast): Z3_ast;
 fun Z3_context.Z3_mk_iff(cfg: self, t1: Z3_ast, t2: Z3_ast): Z3_ast;
 fun Z3_context.Z3_mk_lt(c: self, t1: Z3_ast, t2: Z3_ast): Z3_ast;
 fun Z3_context.Z3_mk_gt(c: self, t1: Z3_ast, t2: Z3_ast): Z3_ast;
 fun Z3_context.Z3_mk_eq(c: self, l: Z3_ast, r: Z3_ast): Z3_ast;
 
 fun Z3_context.Z3_mk_and(cfg: self, num_args: Int, args: Z3_ast[]): Z3_ast;
 fun Z3_context.Z3_mk_or(cfg: self, num_args: Int, args: Z3_ast[]): Z3_ast;
 fun Z3_context.Z3_mk_add(c: self, num_args: Int, args: Z3_ast[]): Z3_ast;
  
 fun Z3_context.Z3_mk_solver(cfg: self): Z3_solver;
 fun Z3_context.Z3_solver_inc_ref(cfg: self, s: Z3_solver);
 fun Z3_context.Z3_solver_dec_ref(cfg: self, s: Z3_solver);
 
 fun Z3_context.Z3_solver_assert(cfg: self, s: Z3_solver, a: Z3_ast);
 fun Z3_context.Z3_solver_check(cfg: self, s: Z3_solver): Int;
 fun Z3_context.Z3_solver_check_assumptions(c: self, s: Z3_solver, num_assumptions: Int, assumptions: Z3_ast[]): Int;
 
 fun Z3_context.Z3_solver_get_unsat_core(c: self, s: Z3_solver): Z3_ast_vector;
 
 fun Z3_context.Z3_mk_string_symbol(cfg: self, s: Char[]): Z3_symbol;
 fun Z3_context.Z3_mk_int_sort(c: self): Z3_sort;
 
 fun Z3_context.Z3_solver_get_model(c: self, s: Z3_solver): Z3_model;
 fun Z3_context.Z3_model_inc_ref(c: self, m: Z3_model);
 fun Z3_context.Z3_model_dec_ref(c: self, m: Z3_model);
 
 fun Z3_context.Z3_set_error_handler(c: self, h: Z3_error_handler);
 
 fun Z3_context.Z3_model_to_string(c: self, m: Z3_model): const<String>;
 fun Z3_context.Z3_model_get_num_consts(c: self, m: Z3_model): Int;
 fun Z3_context.Z3_model_get_const_decl(c: self, m: Z3_model, i: Int): Z3_func_decl;
 fun Z3_context.Z3_get_decl_name(c: self, d: Z3_func_decl): Z3_symbol;
 
 fun Z3_context.Z3_mk_app(c: self, d: Z3_func_decl, num_args: Int, args: Z3_ast[]): Z3_ast;
 fun Z3_context.Z3_model_eval(c: self, m: Z3_model, t: Z3_ast, model_completion: Boolean, v: Z3_ast): Boolean;
 
 fun Z3_context.Z3_del_context(cfg: self);
 
 fun Z3_context.Z3_get_symbol_kind(cfg: self, s: Z3_symbol): Int;
 fun Z3_context.Z3_get_symbol_int(cfg: self, s: Z3_symbol): Int;
 fun Z3_context.Z3_get_symbol_string(cfg: self, s: Z3_symbol): const<String>;
 
 fun Z3_context.Z3_get_ast_kind(c: self, a: Z3_ast): Int;
 fun Z3_context.Z3_get_numeral_string(c: self, a: Z3_ast);
 fun Z3_context.Z3_get_sort(c: self, a: Z3_ast): Z3_sort;
 fun Z3_context.Z3_get_sort_kind(c: self, t: Z3_sort): Int;
 fun Z3_context.Z3_get_sort_name(c: self, d: Z3_sort): Z3_symbol;
 fun Z3_context.Z3_get_bv_sort_size(c: self, t: Z3_sort): Int;
 fun Z3_context.Z3_get_array_sort_domain(c: self, t: Z3_sort): Z3_sort;
 fun Z3_context.Z3_get_array_sort_range(c: self, t: Z3_sort): Z3_sort;

 fun Z3_context.Z3_get_datatype_sort_num_constructors(c: self, t: Z3_sort): Int;
 fun Z3_context.Z3_sort_to_string(c: self, t: Z3_sort): const<String>;
 fun Z3_context.Z3_get_tuple_sort_num_fields(c: self, t: Z3_sort): Int;
 fun Z3_context.Z3_get_tuple_sort_field_decl(c: self, t: Z3_sort, i: Int): Z3_func_decl;
 fun Z3_context.Z3_get_range(c: self, d: Z3_func_decl): Z3_sort;

 automaton Z3_error_handler {
   state Created, Constructed;
   shift Constructed -> self (invoke);
   extendable;
 }
 
 fun Z3_error_handler.invoke(c: Z3_context, e: Int);
}
