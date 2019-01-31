import base64
import copy
import json
import logging
import os
import socket
import sys
from collections import MutableMapping
from threading import Thread
from typing import Union
from enum import Enum


class Tag(Enum):
    REQUEST = 0
    RESPONSE = 1
    CALLBACK_REQUEST = 2
    CALLBACK_RESPONSE = 3
    OPEN_CHANNEL = 4
    DELETE_FROM_PERSISTENCE = 5
    START_BUFFERING = 6
    STOP_BUFFERING = 7


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
        print("Not needed")
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
            raise Exception()
        length = int.from_bytes(length_binary, byteorder='little')
        logging.info("Received length: {}".format(length))

        tag_binary = self.channel.read(4)
        tag = int.from_bytes(tag_binary, byteorder='little')
        logging.info("Received tag: {}".format(tag))

        message_text = self.channel.read(length)
        logging.info("Received message: {}".format(message_text))
        return tag, message_text

    def write(self, data, tag):
        self.channel.write(len(data).to_bytes(4, byteorder='little'))
        self.channel.write(tag.to_bytes(4, byteorder='little'))
        self.channel.write(data)
        self.channel.flush()
        logging.info("Wrote " + data)


class RequestsReceiver():
    @staticmethod
    def encode_return_value(return_value):
        if isinstance(return_value, bytes):
            return base64.b64encode(return_value).decode()
        elif isinstance(return_value, MutableMapping):
            return dict(return_value)
        else:
            return return_value

    @staticmethod
    def decode_args(args):
        decoded_args = []
        for arg in args:
            decoded_arg = ""
            if arg['key']:
                decoded_arg = "{} = ".format(arg['key'])
            if arg['type'] == "string":
                decoded_arg += '"' + arg['value'] + '"'
            else:
                decoded_arg += arg['value']
            decoded_args.append(decoded_arg)
        return ", ".join(decoded_args)

    def prepare_command(self, message):
        if 'import' in message and message['import']:
            prefix = "import {}; ".format(message['import'])
        else:
            prefix = ""
        if 'property' in message and message['property']:
            command = prefix + "{} = {}.{}".format(message['assignedID'],
                                                   message['objectID'],
                                                   message['methodName'])
        elif message['objectID']:
            command = prefix + "{} = {}.{}({})".format(message['assignedID'],
                                                       message['objectID'],
                                                       message['methodName'],
                                                       self.decode_args(message['args']))
        else:
            command = prefix + "{} = {}({})".format(message['assignedID'],
                                                    message['methodName'],
                                                    self.decode_args(message['args']))
        return command

    def __init__(self, channel: UnixSocketServerChannel):
        self.channel = channel
        self.framing = SimpleBinaryFraming(self.channel)
        logging.info("Opened!")

    persistence = {}

    def delete_from_persistence(self, var_name):
        logging.info("Delete {} from persistence".format(var_name))
        del RequestsReceiver.persistence[var_name]

    # def legacy_code(self):
    #     1 + 2
    #     if 'exec' in message:
    #         exec(message['exec'], globals(), local)
    #     if 'eval' in message:
    #         return_value = eval(message['eval'], globals(), local)
    #         logging.info("Result is {}".format(return_value))
    #         logging.info("Result type is {}".format(type(return_value)))
    #         response["return_value"] = self.encode_return_value(return_value)
    #     if 'store' in message:
    #         var_name = message['store']
    #         self.persistence[var_name] = local[var_name]

    def callback_handler(self, template, *args, **kwargs):
        request = copy.deepcopy(template)
        req_args = []
        for arg in args:
            if arg is int or arg is str:
                type = "inplace"
            else:
                type = "persistence"
            request["args"] += {"value": arg, "type": type}

        self.framing.write(json.dumps(request), Tag.CALLBACK_REQUEST)
        tag, value = self.framing.read()
        assert tag == Tag.CALLBACK_RESPONSE
        return value["return_value"]

    def dynamically_inherit_class(self, base, method_names):
        class MyClass(base):
            pass

        new_class = MyClass
        for method_name in method_names:
            callback_wrapper = self.create_callback(method_name)
            new_class.__setattr__(method_name, callback_wrapper)
        return new_class

    def create_callback(self, callback_name):
        request = {
            "methodName": callback_name,
            "objectID": "",
            "args": [],
            "static": False,
            "doGetReturnValue": False,
            "property": False,
            "assignedID": "",
        }

        return lambda *args, **kwargs: self.callback_handler(request, args, kwargs)

    def receive(self):
        while True:
            tag, message_text = self.framing.read()
            if message_text == "exit":
                logging.info("Exit")
                break
            message = json.loads(message_text)
            local = RequestsReceiver.persistence.copy()
            response = {}
            if tag == Tag.DELETE_FROM_PERSISTENCE.value and 'delete' in message and message['delete']:
                self.delete_from_persistence(message['delete'])
            elif tag == Tag.REQUEST and 'methodName' in message:
                command = self.prepare_command(message)
                logging.debug("Exec: " + command)
                logging.debug("Persistence before exec is {}".format(local))
                exec(command, globals(), local)
                var_name = message['assignedID']
                RequestsReceiver.persistence[var_name] = local[var_name]
                if 'doGetReturnValue' in message and message['doGetReturnValue']:
                    return_value = eval(message['assignedID'], globals(), local)
                    response["return_value"] = self.encode_return_value(return_value)
            logging.info("Persistence is {}".format(RequestsReceiver.persistence))
            response_text = json.dumps(response)
            self.framing.write(response_text, Tag.RESPONSE.value)

        logging.info("Exit")
        self.framing.channel.close()


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
