import os
import logging

import requests
import sys

import json

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

    def receive(self):
        while True:
            length_text = self.input.read(4)
            if len(length_text) == 0:
                logging.info("Empty")
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
            exec(message['exec'], globals(), local)
            response = {}
            if 'eval' in message:
                res = eval(message['eval'], globals(), local)
                logging.info("Result is {}".format(res))
                response["return_value"] = str(res)
            if 'store' in message:
                var_name = message['store']
                self.persistence[var_name] = local[var_name]
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
