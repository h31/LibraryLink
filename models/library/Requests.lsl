library Requests {
 imports {
   requests;
 }

 types {
   Requests (Requests);
   Response (Response);
   URL (String);
   StatusCode (int);
   Content (ByteArray);
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

 fun Requests.get(url: URL, headers: Dict): Response {
   static "requests";
 }

 fun Response.status_code(): StatusCode {
   property "type" = "get";
 }
}