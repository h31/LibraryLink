import os
import logging

import requests
import sys

class RequestsReceiver():
    def __init__(self):
        if not os.path.exists("/tmp/wrapperfifo_input"):
            os.mkfifo("/tmp/wrapperfifo_input")
        if not os.path.exists("/tmp/wrapperfifo_output"):
            os.mkfifo("/tmp/wrapperfifo_output")

        sys.stdout.write("Done\n")
        sys.stdout.flush()

        # self.input = open("/tmp/wrapperfifo_input", "r")
        # self.output = open("/tmp/wrapperfifo_output", "w")

        logging.info("Opened!")

    def receive(self):
        self.input = open("/tmp/wrapperfifo_input", "r")
        self.output = open("/tmp/wrapperfifo_output", "w")

        while True:
            message = self.input.read(1024)
            logging.info("Received: {}".format(message))
            if len(message) == 0:
                logging.info("Empty")
                break
            if message == "exit":
                logging.info("Exit")
                break
            exec_part, eval_part = message.split(";;")
            exec(exec_part)
            res = eval(eval_part, globals(), locals())
            logging.info("Result is {}".format(res))
            self.output.write(str(res))
            self.output.flush()
            logging.info("Wrote")

        logging.info("Exit")
        self.input.close()
        self.output.close()

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    receiver = RequestsReceiver()
    receiver.receive()
