library Python {
 types {
   str (native:String);
   Dict (native:Map);
   int (native:int);
 }
 automaton Dict {
   state Created;
   state Constructed;
   shift Created -> Constructed (dict);
 }

 fun Dict.dict(): Headers {
   static "";
 }
}