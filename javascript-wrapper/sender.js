"use strict";

// var fruits = require("./fruits");
// var container = document.getElementById("container");

protobuf.load("exchange.proto", function(err, root) {
    if (err)
        throw err;

    connect(root);

    // // Exemplary payload
    // var payload = { awesomeField: "AwesomeString" };

    // // Verify the payload if necessary (i.e. when possibly incomplete or invalid)
    // var errMsg = AwesomeMessage.verify(payload);
    // if (errMsg)
    //     throw Error(errMsg);
    //
    // // Create a new message
    // var message = AwesomeMessage.create(payload); // or use .fromObject if conversion is necessary
    //
    // // Encode a message to an Uint8Array (browser) or Buffer (node)
    // var buffer = AwesomeMessage.encode(message).finish();
    // // ... do something with buffer
    //
    // // Decode an Uint8Array (browser) or Buffer (node) to a message
    // var message = AwesomeMessage.decode(buffer);
    // // ... do something with message
    //
    // // If the application uses length-delimited buffers, there is also encodeDelimited and decodeDelimited.
    //
    // // Maybe convert the message back to a plain object
    // var object = AwesomeMessage.toObject(message, {
    //     longs: String,
    //     enums: String,
    //     bytes: String,
    //     // see ConversionOptions
    // });
});

function connect(root) {
    const request = root.lookupType("exchange.Request");
    const methodCall = root.lookupType("exchange.MethodCallRequest");
    const innerMessage = methodCall.create({
        "method_name": "get",
        "object_id": "requests",
    });
    const message = request.create({
        "method_call": innerMessage
    });
    console.log(request.verify(message));

    var messageBuffer = request.encode(message).finish();

    let socket = new WebSocket("ws://localhost:23456");
    socket.binaryType = 'arraybuffer';

    socket.addEventListener('open', function (event) {
        let headerBuffer = new ArrayBuffer(8);
        let view = new Uint32Array(headerBuffer);
        view[0] = messageBuffer.byteLength;
        view[1] = 0; // REQUEST

        socket.send(headerBuffer);
        socket.send(messageBuffer);
    });
}

// container.textContent = fruits.join(", ");
//
// let socket = new WebSocket("ws://localhost:12345");
//
// const handler = function (socket) {
//     socket.send();
//     console.log(`Calculate sum: ${argumentsList}`);
//     // expected output: "Calculate sum: 1,2"
//
//     return target(argumentsList[0], argumentsList[1]) * 10;
// };
//
// var proxy1 = new Proxy(socket, handler);
//
// console.log(sum(1, 2));
// // expected output: 3
// console.log(proxy1(1, 2));
// // expected output: 30
