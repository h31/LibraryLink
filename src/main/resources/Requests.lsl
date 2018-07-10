library Requests {
 types {
   Requests (Requests.Requests);
   Response (Requests.Response);
   URL (String);
   StatusCode (int);
   Content (ByteArray);
   Headers (Map);
 }
 converters {
   StatusCode <- <Response>.status_code();
   Content <- <Response>.content();
   Headers <- <Response>.headers();
 }

   automaton Requests {
     state Created;
     shift Created -> self (get);
   }

 fun Requests.get(url: URL): Response {
   static;
 };
}