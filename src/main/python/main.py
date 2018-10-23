import base64
import json
import logging
import socket
import sys
from collections import MutableMapping


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
        print("Opening socket...")
        self.server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.server.bind(base_path)
        self.socket = self.server.accept()

    def write(self, data):
        return self.socket.write(data)

    def read(self, length):
        return self.socket.read(length)

    def flush(self):
        self.socket.flush()

    def close(self):
        self.socket.close()
        self.socket.close()



class SimpleTextFraming:
    def __init__(self, channel: FIFOChannelManager):
        self.channel = channel

    def read(self):
        length_text = self.channel.read(4)
        if len(length_text) == 0:
            logging.info("Empty request, exiting")
            raise Exception()
        logging.info("Received length: {}".format(length_text))
        length = int(length_text)
        message_text = self.channel.read(length)
        logging.info("Received message: {}".format(message_text))
        return message_text

    def write(self, data):
        self.channel.write("{0:04d}".format(len(data)))
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

    def __init__(self, base_path):
        self.channel = FIFOChannelManager(base_path)
        self.framing = SimpleTextFraming(self.channel)
        self.persistence = {}
        logging.info("Opened!")

    def delete_from_persistence(self, var_name):
        logging.info("Delete {} from persistence".format(var_name))
        del self.persistence[var_name]

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

    def receive(self):
        while True:
            message_text = self.framing.read()
            if message_text == "exit":
                logging.info("Exit")
                break
            message = json.loads(message_text)
            local = self.persistence.copy()
            response = {}
            if 'delete' in message and message['delete']:
                self.delete_from_persistence(message['delete'])
            if 'methodName' in message:
                command = self.prepare_command(message)
                logging.debug("Exec: " + command)
                logging.debug("Persistence before exec is {}".format(local))
                exec(command, globals(), local)
                var_name = message['assignedID']
                self.persistence[var_name] = local[var_name]
                if 'doGetReturnValue' in message and message['doGetReturnValue']:
                    return_value = eval(message['assignedID'], globals(), local)
                    response["return_value"] = self.encode_return_value(return_value)
            logging.info("Persistence is {}".format(self.persistence))
            response_text = json.dumps(response)
            self.framing.write(response_text)

        logging.info("Exit")
        self.framing.channel.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    if len(sys.argv) < 2:
        logging.critical("FIFO base path required, cannot proceed")
        exit(1)
    base_path = sys.argv[1]
    receiver = RequestsReceiver(base_path)
    receiver.receive()
