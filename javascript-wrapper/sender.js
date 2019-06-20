var fruits = require("./fruits");
var container = document.getElementById("container");

container.textContent = fruits.join(", ");

let socket = new WebSocket("ws://localhost:12345");

const handler = function (socket) {
    socket.send();
    console.log(`Calculate sum: ${argumentsList}`);
    // expected output: "Calculate sum: 1,2"

    return target(argumentsList[0], argumentsList[1]) * 10;
};

var proxy1 = new Proxy(socket, handler);

console.log(sum(1, 2));
// expected output: 3
console.log(proxy1(1, 2));
// expected output: 30
