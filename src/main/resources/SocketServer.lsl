library SocketServer {
 imports {
   socketserver;
 }

 types {
   BaseRequestHandler (socketserver.BaseRequestHandler);
   Request (socketserver.Request);
   TCPServer (socketserver.TCPServer);
   Class (native:Handle);
   Size (int);
 }

 converters {
   Content <- <Response>.content();
   Headers <- <Response>.headers();
 }

 automaton BaseRequestHandler {
   inheritable;
   state Constructed;
   shift Constructed -> self (request, handle);
 }

 automaton Request {
   state Constructed;
   shift Constructed -> self (recv);
 }

 automaton TCPServer {
   state Created, Constructed;
   shift Created -> Constructed (init);
   shift Constructed -> self (serve_forever);
 }

 fun BaseRequestHandler.request(): Request;

 fun BaseRequestHandler.handle() {
   abstract;
 }

 fun Request.recv(size: Size): Bytes;

 fun TCPServer.init(server_addr: Tuple, handler: Class, bind_and_activate: Boolean = true): StatusCode {
   constructor;
 }

 fun TCPServer.serve_forever();
}