library C {
 types {
   String (string);
   Int (int);
   Array (void);
   Any (void);
 }
 
 automaton Array {
    state Created, Constructed;
    shift Created -> Constructed (calloc);
    shift Constructed -> self (set);
    shift Constructed -> self (get);
 }
 
 fun Array.calloc(nmemb: Int, size: Int): Array;
 fun Array.set(index: Int, element: Any) {
   template "{}[{}] = {}";
 }
 fun Array.get(index: Int): Any {
   template "{}[{}]";
 }
}


