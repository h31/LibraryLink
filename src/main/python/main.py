import base64
import copy
import json
import logging
import os
import socket
import sys
import threading
from collections import MutableMapping
from threading import Thread
from typing import Union
from enum import Enum
import exchange_pb2


class AtomicCounter:
    """An atomic, thread-safe incrementing counter.
    https://gist.github.com/benhoyt/8c8a8d62debe8e5aa5340373f9c509c7
    """

    def __init__(self, initial=0):
        """Initialize a new atomic counter to given initial value (default 0)."""
        self.value = initial
        self._lock = threading.Lock()

    def increment(self, num=1):
        """Atomically increment the counter by num (default 1) and return the
        new value.
        """
        with self._lock:
            self.value += num
            return self.value - num  # TODO


class Tag(Enum):
    REQUEST = 0
    RESPONSE = 1
    CALLBACK_REQUEST = 2
    CALLBACK_RESPONSE = 3
    OPEN_CHANNEL = 4
    DELETE_FROM_PERSISTENCE = 5
    START_BUFFERING = 6
    STOP_BUFFERING = 7
    # IMPORT_REQUEST = 8
    # CONSTRUCTOR_REQUEST = 9
    # EVAL_REQUEST = 10
    # DYNAMIC_INHERIT_REQUEST = 11


class FIFOChannelManager:
    def __init__(self, base_path):
        input_filename = "{}_to_receiver_{}".format(base_path, "main")
        output_filename = "{}_from_receiver_{}".format(base_path, "main")

        self.input = open(input_filename, "r")
        self.output = open(output_filename, "w")

    def write(self, data):
        return self.output.write(data)

    def read(self, length):
        return self.input.read(length)

    def flush(self):
        self.output.flush()

    def close(self):
        self.output.close()
        self.input.close()


class UnixSocketServerChannelManager:
    def __init__(self, base_path):
        print("Remove previous socket...")
        if os.path.exists(base_path):
            os.unlink(base_path)
        print("Opening socket...")
        self.server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.server.bind(base_path)
        self.server.listen(5)

    def accept(self):
        socket, address = self.server.accept()
        return UnixSocketServerChannel(socket)


class UnixSocketServerChannel:
    def __init__(self, socket):
        self.socket = socket

    def write(self, data: bytes):
        return self.socket.send(data)

    def read(self, length):
        return self.socket.recv(length)

    def flush(self):
        pass
        # print("Not needed")
        # self.socket.flush()

    def close(self):
        self.socket.close()


class SimpleBinaryFraming:
    def __init__(self, channel: Union[FIFOChannelManager, UnixSocketServerChannel]):
        self.channel = channel

    def read(self):
        length_binary = self.channel.read(4)
        if len(length_binary) == 0:
            logging.info("Empty request, exiting")
            exit(0)  # TODO
        length = int.from_bytes(length_binary, byteorder='little')
        logging.info("Received length: {}".format(length))

        tag_binary = self.channel.read(4)
        tag = int.from_bytes(tag_binary, byteorder='little')
        logging.info("Received tag: {}".format(tag))

        message_text = self.channel.read(length)
        return tag, message_text

    def write(self, data, tag):
        self.channel.write(len(data).to_bytes(4, byteorder='little'))
        self.channel.write(tag.to_bytes(4, byteorder='little'))
        self.channel.write(data)
        self.channel.flush()


class RequestsReceiver():
    @staticmethod
    def decode_arg(arg):
        if arg.type == exchange_pb2.Argument.INPLACE and arg.HasField("string_value"):
            decoded_arg = '"' + arg.string_value + '"'
        elif arg.type == exchange_pb2.Argument.INPLACE and arg.HasField("int_value"):
            decoded_arg = arg.int_value
        elif arg.type == exchange_pb2.Argument.PERSISTENCE:
            decoded_arg = arg.string_value
        else:
            raise Exception()

        if arg.key:
            decoded_arg = "{} = {}".format(arg.key, decoded_arg)
        return decoded_arg

    @staticmethod
    def decode_args(args):
        decoded_args = []
        for arg in args:
            decoded_arg = RequestsReceiver.decode_arg(arg)
            decoded_args.append(decoded_arg)
        return ", ".join(decoded_args)

    def prepare_command(self, message):
        if message.property:
            command = "{} = {}.{}".format(message.assignedID,
                                          message.objectID,
                                          message.methodName)
        elif message.objectID:
            command = "{} = {}.{}({})".format(message.assignedID,
                                              message.objectID,
                                              message.methodName,
                                              self.decode_args(message.arg))
        else:
            command = "{} = {}({})".format(message.assignedID,
                                           message.methodName,
                                           self.decode_args(message.arg))
        return command

    def format_executed_code(self, message):
        args = [self.decode_arg(x) for x in message.args]
        command = message.executedCode.format(*tuple(args))
        return message.assignedID + " = " + command

    def execute_command(self, command):
        logging.debug("Persistence before exec is {}".format(RequestsReceiver.persistence))  # TODO: Thread safety?
        logging.debug("Exec: " + command)
        try:
            exec(command, RequestsReceiver.persistence_globals, RequestsReceiver.persistence)
        except BaseException as e:
            return e
        return None
        # var_name = message.assignedID
        # RequestsReceiver.persistence[var_name] = local[var_name]

    def __init__(self, channel: UnixSocketServerChannel):
        self.channel = channel
        self.framing = SimpleBinaryFraming(self.channel)
        logging.info("Opened!")

    persistence = {}
    persistence_globals = globals()
    callback_index = AtomicCounter()

    def delete_from_persistence(self, var_name):
        logging.info("Delete {} from persistence".format(var_name))
        if var_name in RequestsReceiver.persistence:
            del RequestsReceiver.persistence[var_name]
        elif var_name in RequestsReceiver.persistence_globals:
            del RequestsReceiver.persistence_globals[var_name]

    # def legacy_code(self):
    #     1 + 2
    #     if 'exec' in message:
    #         exec(message.exec, globals(), local)
    #     if 'eval' in message:
    #         return_value = eval(message.eval, globals(), local)
    #         logging.info("Result is {}".format(return_value))
    #         logging.info("Result type is {}".format(type(return_value)))
    #         response["return_value"] = self.encode_return_value(return_value)
    #     if 'store' in message:
    #         var_name = message.store
    #         self.persistence[var_name] = local[var_name]

    def callback_argument(self, arg):
        if isinstance(arg, int) or isinstance(arg, str):
            return {"value": arg, "type": "inplace", "key": None}
        else:
            for key, value in RequestsReceiver.persistence.items():
                if arg is value:
                    logging.info("Found in the persistence!")
                    return {"value": key, "type": "persistence", "key": None}
            key = "callback_var" + str(RequestsReceiver.callback_index.increment())
            logging.info("Put in the persistence as " + key)
            RequestsReceiver.persistence[key] = arg
            return {"value": key, "type": "persistence", "key": None}

    def callback_handler(self, current_object, template, *args, **kwargs):
        request = copy.deepcopy(template)
        request["assignedID"] = self.callback_argument(current_object)["value"]  # TODO
        for arg in args:
            request["args"].append(self.callback_argument(arg))

        self.framing.write(json.dumps(request).encode(), Tag.CALLBACK_REQUEST.value)
        while True:
            tag, value = self.framing.read()
            if tag == Tag.CALLBACK_RESPONSE.value:
                message = json.loads(value)
                return message["return_value"]
            elif tag == Tag.REQUEST.value:
                self.process_request(value, tag)

    def dynamically_inherit_class(self, import_name, class_name, base, methods):
        base_class = getattr(RequestsReceiver.persistence[import_name], base)

        class MyClass(base_class):
            pass

        new_class = MyClass
        for method_name, method_args in methods.items():
            callback_wrapper = self.create_callback(class_name, method_name)
            setattr(new_class, method_name, callback_wrapper)
        return new_class

    def create_callback(self, class_name, callback_name):
        request = {
            "methodName": callback_name,
            "objectID": class_name,
            "args": [],
            "static": False,
            "doGetReturnValue": False,
            "property": False,
            "assignedID": "",
        }

        return lambda current_object, *args, **kwargs: self.callback_handler(current_object, request, args, kwargs)

    def receive(self):
        try:
            while True:
                tag, message_text = self.framing.read()
                if message_text == "exit":
                    logging.info("Exit")
                    break
                self.process_request(message_text, tag)
        except Exception:
            import _thread
            _thread.interrupt_main()
            raise EOFError()  # TODO
        finally:
            logging.info("Exit")
            self.framing.channel.close()

    def put_return_value(self, var_name, response):
        # return_value = eval(message.assignedID, RequestsReceiver.persistence_globals, local)
        return_value = RequestsReceiver.persistence[var_name]
        if isinstance(return_value, int):
            response.return_value_int = return_value
        elif isinstance(return_value, str):
            response.return_value_string = return_value
        else:
            logging.info("Cannot encode value " + return_value)

    def process_request(self, message_text, tag):
        request = exchange_pb2.Request()
        request.ParseFromString(message_text)
        logging.info("Received message: {}".format(request))
        response = exchange_pb2.ChannelResponse()
        if tag == Tag.DELETE_FROM_PERSISTENCE.value:
            message = json.loads(message_text)  # TODO
            self.delete_from_persistence(message.delete)
        elif tag == Tag.REQUEST.value and request.HasField("importation"):
            self.execute_command("import {};".format(request.importation.importedName))
            logging.info("Imported")
        elif tag == Tag.REQUEST.value and request.HasField("method_call"):
            command = self.prepare_command(request.method_call)
            exception = self.execute_command(command)
            if exception is not None:
                response.exception_message = repr(exception)
            elif request.method_call.doGetReturnValue:
                var_name = request.method_call.assignedID
                self.put_return_value(var_name, response)
        elif tag == Tag.REQUEST.value and request.HasField("eval"):
            command = self.format_executed_code(request.eval)
            exception = self.execute_command(command)
            if exception is not None:
                response.exception_message = repr(exception)
            elif request.eval.doGetReturnValue:
                var_name = request.eval.assignedID
                self.put_return_value(var_name, response)
        elif tag == Tag.REQUEST.value and request.HasField("dynamic_inherit"):
            new_class = self.dynamically_inherit_class(request.dynamic_inherit.importName, request.dynamic_inherit.automatonName,
                                                       request.dynamic_inherit.inherits, request.dynamic_inherit.methodArguments)
            var_name = request.dynamic_inherit.assignedID
            RequestsReceiver.persistence[var_name] = new_class
        elif tag == Tag.REQUEST.value and request.HasField("constructor"):
            command = "{} = {}({})".format(request.constructor.assignedID,
                                           request.constructor.className,
                                           self.decode_args(request.constructor.args))
            self.execute_command(command)
        else:
            raise Exception(message_text)
        logging.info("Persistence is {}", RequestsReceiver.persistence)
        logging.info("Response is %s", response)
        response_text = response.SerializeToString()
        self.framing.write(response_text, Tag.RESPONSE.value)


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    if len(sys.argv) < 2:
        logging.critical("FIFO base path required, cannot proceed")
        exit(1)
    base_path = sys.argv[1]
    channel_manager = UnixSocketServerChannelManager(base_path)
    while True:
        channel = channel_manager.accept()
        receiver = RequestsReceiver(channel)
        thread = Thread(target=receiver.receive)
        thread.start()
        # receiver.receive()
