import base64
import json
import logging
import os
from collections import MutableMapping

import sys


class RequestsReceiver():
    def __init__(self):
        if not os.path.exists("/tmp/wrapperfifo_input"):
            os.mkfifo("/tmp/wrapperfifo_input")
        if not os.path.exists("/tmp/wrapperfifo_output"):
            os.mkfifo("/tmp/wrapperfifo_output")

        sys.stdout.write("Done\n")
        sys.stdout.flush()

        self.input = open("/tmp/wrapperfifo_input", "r")
        self.output = open("/tmp/wrapperfifo_output", "w")

        self.persistence = {}

        logging.info("Opened!")

    def encode_return_value(self, return_value):
        if isinstance(return_value, bytes):
            return base64.b64encode(return_value).decode()
        elif isinstance(return_value, MutableMapping):
            return dict(return_value)
        else:
            return return_value

    def decode_args(self, args):
        decoded_args = map(lambda x: '"' + x + '"' if isinstance(x, str) else "ERROR", args)
        return ", ".join(decoded_args)

    def receive(self):
        while True:
            length_text = self.input.read(4)
            if len(length_text) == 0:
                logging.info("Empty request, exiting")
                break
            logging.info("Received length: {}".format(length_text))
            length = int(length_text)
            message_text = self.input.read(length)
            logging.info("Received message: {}".format(message_text))
            if message_text == "exit":
                logging.info("Exit")
                break
            message = json.loads(message_text)
            local = self.persistence.copy()
            if 'exec' in message:
                exec(message['exec'], globals(), local)
            response = {}
            if 'eval' in message:
                return_value = eval(message['eval'], globals(), local)
                logging.info("Result is {}".format(return_value))
                logging.info("Result type is {}".format(type(return_value)))
                response["return_value"] = self.encode_return_value(return_value)
            if 'store' in message:
                var_name = message['store']
                self.persistence[var_name] = local[var_name]
            if 'methodName' in message:
                if 'import' in message:
                    prefix = "import {}; ".format(message['import'])
                else:
                    prefix = ""
                command = prefix + "{} = {}.{}({})".format(message['assignedID'],
                                                           message['objectID'],
                                                           message['methodName'],
                                                           self.decode_args(message['args']))
                logging.debug(command)
                exec(command, globals(), local)
                var_name = message['assignedID']
                self.persistence[var_name] = local[var_name]
                if 'doGetReturnValue' in message and message['doGetReturnValue'] == True:
                    return_value = eval(message['assignedID'], globals(), local)
                    response["return_value"] = self.encode_return_value(return_value)
            logging.info("Persistence is {}".format(self.persistence))
            response_text = json.dumps(response)
            self.output.write("{0:04d}".format(len(response_text)))
            self.output.write(response_text)
            self.output.flush()
            logging.info("Wrote " + response_text)

        logging.info("Exit")
        self.input.close()
        self.output.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    receiver = RequestsReceiver()
    receiver.receive()
