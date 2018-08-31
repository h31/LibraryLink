import base64
import json
import logging
import os
from collections import MutableMapping

import sys


class RequestsReceiver():
    def __init__(self, base_path):
        input = "{}_to_receiver_{}".format(base_path, "main")
        output = "{}_from_receiver_{}".format(base_path, "main")
        if not os.path.exists(input):
            os.mkfifo(input)
        if not os.path.exists(output):
            os.mkfifo(output)

        self.input = open(input, "r")
        self.output = open(output, "w")

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
        decoded_args = []
        for arg in args:
            if arg['type'] == "string":
                decoded_args.append('"' + arg['value'] + '"')
            else:
                decoded_args.append(arg['value'])
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
            if 'delete' in message and message['delete']:
                var_name = message['delete']
                logging.info("Delete {} from persistence".format(var_name))
                del self.persistence[var_name]
            if 'eval' in message:
                return_value = eval(message['eval'], globals(), local)
                logging.info("Result is {}".format(return_value))
                logging.info("Result type is {}".format(type(return_value)))
                response["return_value"] = self.encode_return_value(return_value)
            if 'store' in message:
                var_name = message['store']
                self.persistence[var_name] = local[var_name]
            if 'methodName' in message:
                if 'import' in message and message['import']:
                    prefix = "import {}; ".format(message['import'])
                else:
                    prefix = ""
                if 'property' in message and message['property'] == True:
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
                logging.debug("Exec: " + command)
                logging.debug("Persistence before exec is {}".format(local))
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
    if len(sys.argv) < 2:
        logging.critical("FIFO base path required, cannot proceed")
        exit(1)
    base_path = sys.argv[1]
    receiver = RequestsReceiver(base_path)
    receiver.receive()
