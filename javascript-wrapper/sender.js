"use strict";

import WebSocketAsPromised from 'websocket-as-promised';
import protobuf from "protobufjs";

// var fruits = require("./fruits");
// var container = document.getElementById("container");

async function getResponse(socket) {
    return new Promise(function (resolve) {
        socket.onMessage.addOnceListener(data => resolve(data));
    });
}

(async () => {
    try {
        let root = await protobuf.load("exchange.proto");
        const request = root.lookupType("exchange.Request");
        const methodCall = root.lookupType("exchange.MethodCallRequest");
        const innerMessage = methodCall.create({
            methodName: "get",
            objectId: "requests",
        });
        const message = request.create({
            methodCall: innerMessage
        });
        console.log(request.verify(message));

        var payloadBuffer = request.encode(message).finish();

        let socket = new WebSocketAsPromised("ws://localhost:7000/librarylink/test");
        socket.binaryType = 'arraybuffer';

        await socket.open();

        let messageBuffer = new ArrayBuffer(4 + payloadBuffer.length);

        let headerView = new Uint8Array(messageBuffer, 0, 1);
        headerView[0] = 0; // REQUEST

        let payloadView = new Uint8Array(messageBuffer, 4);
        payloadView.set(payloadBuffer);

        socket.send(messageBuffer);

        let responseMessage = await getResponse(socket);

        let response = request.decode(new Uint8Array(responseMessage));
        alert(response.toJSON());

        await socket.close();
    } catch (err) {
        alert(err);
    } finally {

    }
})();

