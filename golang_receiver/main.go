package main

import (
	"bufio"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"github.com/dave/jennifer/jen"
	"io/ioutil"
	"log"
	"net"
	"os"
	"syscall"
)

type Request struct {
	MethodName       string
	ObjectID         string
	Args             []Argument
	Import           string
	DoGetReturnValue bool
	AssignedID       string
	Property         bool
	Static           bool
}

type Argument struct {
	Type  string
	Value interface{}
	Key   *string
}

type ChannelResponse struct {
	ReturnValue *string `json:"return_value,omitempty"`
}

var persistence map[string]interface{} = make(map[string]interface{})

func decodeArg(argument Argument) {

}

func process(request Request) ChannelResponse {
	response := ChannelResponse{}
	switch request.MethodName {
	case "NewFile":
		result := jen.NewFile(request.Args[0].Value.(string))
		persistence[request.AssignedID] = result
	case "ToGroup":
		obj := persistence[request.ObjectID].(*jen.File)
		persistence[request.AssignedID] = obj.Group
	case "Func":
		obj := persistence[request.ObjectID].(*jen.Group)
		result := obj.Func()
		persistence[request.AssignedID] = result
	case "Id":
		obj := persistence[request.ObjectID].(*jen.Statement)
		result := obj.Id(request.Args[0].Value.(string))
		persistence[request.AssignedID] = result
	case "Params":
		obj := persistence[request.ObjectID].(*jen.Statement)
		result := obj.Params()
		persistence[request.AssignedID] = result
	case "Block":
		obj := persistence[request.ObjectID].(*jen.Statement)
		result := obj.Block()
		persistence[request.AssignedID] = result
	case "GoString":
		obj := persistence[request.ObjectID].(*jen.File)
		result := obj.GoString()
		persistence[request.AssignedID] = result
		response.ReturnValue = &result
	}
	return response
}

func jenUsage() {
	f := jen.NewFile("main")
	f.Func().Id("main").Params().Block(
		jen.Qual("fmt", "Println").Call(jen.Lit("Hello, world")),
	)
	f.GoString()
	fmt.Printf("%#v", f)
}

func readNumBytes(reader *bufio.Reader, num int) ([]byte, error) {
	buffer := make([]byte, num)
	marker := 0
	for marker < num {
		println(len(buffer[marker:]))
		receivedDataLength, err := reader.Read(buffer[marker:])
		if err != nil {
			return buffer, err
		}
		marker += receivedDataLength
	}
	return buffer, nil
}

func handler(conn net.Conn) {
	bufferedReader := bufio.NewReaderSize(conn, 16*1024)
	bufferedWriter := bufio.NewWriterSize(conn, 16*1024)
	for {
		requestLengthBinary, err := readNumBytes(bufferedReader, 4)
		if err != nil {
			return
		}
		reqLen := int(binary.LittleEndian.Uint32(requestLengthBinary))
		log.Println("Length: ", reqLen)

		readNumBytes(bufferedReader, 4) // TODO: Tag

		payload, err := readNumBytes(bufferedReader, reqLen)
		log.Println("Payload: ", payload)

		var req Request
		err = json.Unmarshal(payload, &req)
		if err != nil {
			log.Fatal("Unmarshal error: ", err)
		}

		response := process(req)

		responseData, err := json.Marshal(response)

		encoded := make([]byte, 4)
		binary.LittleEndian.PutUint32(encoded, uint32(len(responseData)))
		_, err = bufferedWriter.Write(encoded)

		binary.LittleEndian.PutUint32(encoded, uint32(1)) // TODO
		_, err = bufferedWriter.Write(encoded)

		_, err = bufferedWriter.Write(responseData)
		if err != nil {
			log.Fatal("Writing client error: ", err)
		}
		bufferedWriter.Flush()
	}
}

func main() {
	log.SetOutput(ioutil.Discard)
	log.SetFlags(0)
	basePath := os.Args[1]
	//input, _ := os.Open(fmt.Sprintf("%s_to_receiver_%s", basePath, "main"))
	//output, _ := os.Open(fmt.Sprintf("%s_from_receiver_%s", basePath, "main"))

	os.Remove(basePath)
	syscall.Unlink(basePath)

	ln, err := net.Listen("unix", basePath)
	if err != nil {
		log.Fatal("Listen error: ", err)
	}
	for {
		fd, err := ln.Accept()
		log.Println("Accepted")
		if err != nil {
			log.Fatal("Accept error: ", err)
		}

		go handler(fd)
	}
}
