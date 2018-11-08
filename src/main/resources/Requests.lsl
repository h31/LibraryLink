library Requests {
 imports {
   requests;
 }
 types {
   Requests (Requests.Requests);
   Response (Requests.Response);
   URL (String);
   StatusCode (int);
   Content (ByteArray);
   Headers (Map);
 }
 converters {
   Content <- <Response>.content();
   Headers <- <Response>.headers();
 }

 automaton Requests {
   state Constructed;
   shift Constructed -> self (get);
 }

 automaton Response {
   state Constructed;
   shift Constructed -> self (status_code);
 }

 fun Requests.get(url: URL): Response {
   static;
 }

 fun Response.status_code(): StatusCode;
}