import os
import logging

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)

    if not os.path.exists("/tmp/wrapperfifo_input"):
        os.mkfifo("/tmp/wrapperfifo_input")
    if not os.path.exists("/tmp/wrapperfifo_output"):
        os.mkfifo("/tmp/wrapperfifo_output")

    input = open("/tmp/wrapperfifo_input", "r")
    output = open("/tmp/wrapperfifo_output", "w")

    logging.info("Opened!")

    while True:
        message = input.read(1024)
        logging.info("Received")
        if len(message) == 0:
            logging.info("Empty")
            break
        if message == "exit":
            logging.info("Exit")
            break
        print(message)
        res = eval(message)
        logging.info("Result is {}".format(res))
        output.write(res)
        output.flush()
        logging.info("Wrote")

    logging.info("Exit")
    input.close()
    output.close()
