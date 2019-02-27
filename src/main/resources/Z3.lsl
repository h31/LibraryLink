library Z3 {
 imports {
   z3.h;
 }

 types {
   Z3_config (Z3_config);
   Z3_context (Z3_context);
   Z3_solver (Z3_solver);
   Z3_sort (Z3_sort);
   Z3_symbol (Z3_symbol);
   Z3_ast (Z3_ast);
 }

 converters {
   Content <- <Response>.content();
   Headers <- <Response>.headers();
 }

 automaton Z3_config {
   state Created, Constructed, Closed;
   shift Created -> Constructed (Z3_mk_config);
   shift Constructed -> self (Z3_mk_context);
   shift Constructed -> Closed (Z3_del_config);
 }

   fun Z3_config.Z3_mk_config(): Z3_config;
   fun Z3_config.Z3_mk_context(cfg: self): Z3_context;
   fun Z3_config.Z3_del_config(cfg: self);

  automaton Z3_context {
    state Constructed;
    shift Constructed -> self (Z3_mk_bool_sort);
    shift Constructed -> self (Z3_mk_int_symbol, Z3_mk_const, Z3_mk_not, Z3_mk_and, Z3_mk_or, Z3_mk_iff);
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

  fun Z3_context.Z3_mk_const(cfg: self, s: Z3_symbol, ty: Z3_sort): Z3_ast;
  fun Z3_context.Z3_mk_not(cfg: self): Z3_ast;
  fun Z3_context.Z3_mk_and(cfg: self): Z3_ast;
  fun Z3_context.Z3_mk_or(cfg: self): Z3_ast;
  fun Z3_context.Z3_mk_iff(cfg: self): Z3_ast;
}