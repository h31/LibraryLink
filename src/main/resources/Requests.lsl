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
   Headers (Headers);
 }

 converters {
   Content <- <Response>.content();
   Headers <- <Response>.headers();
 }

 automaton Requests {
   state Constructed;
   shift Constructed -> self (get);
 }

 automaton Headers {
   state Created, Constructed;
   shift Created -> Constructed (dict);
 }

 automaton Response {
   state Constructed;
   shift Constructed -> self (status_code);
 }

 fun Requests.get(url: URL, headers: Headers): Response {
   static "requests";
 }

 fun Response.status_code(): StatusCode {
   property "type" = "get";
 }

 fun Headers.dict(): Headers {
   static "";
 }
}