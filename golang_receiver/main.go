package main

import (
	"bufio"
	"encoding/binary"
	"exchange"
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

const (
	REQUEST                 = 0
	RESPONSE                = 1
	CALLBACK_REQUEST        = 2
	CALLBACK_RESPONSE       = 3
	OPEN_CHANNEL            = 4
	DELETE_FROM_PERSISTENCE = 5
	START_BUFFERING         = 6
	STOP_BUFFERING          = 7
)

type Argument struct {
	Type  string
	Value interface{}
	Key   *string
}

type ChannelResponse struct {
	ReturnValue interface{} `json:"return_value,omitempty"`
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
		receivedDataLength, err := reader.Read(buffer[marker:])
		if err != nil {
			return buffer, err
		}
		marker += receivedDataLength
	}
	return buffer, nil
}

func readProtobuf(request *Request, data []byte) {
	rawReq := &exchange.MethodCallRequest{}
	err := rawReq.Unmarshal(data)
	if err != nil {
		println(err.Error())
		return // TODO
	}
	request.MethodName = rawReq.MethodName
	request.ObjectID = rawReq.ObjectID
	request.Args = make([]Argument, len(rawReq.Args))
	for i, arg := range rawReq.Args {
		switch arg.Type {
		case exchange.MethodCallRequest_PERSISTENCE:
			request.Args[i].Type = "persistence"
		case exchange.MethodCallRequest_INPLACE:
			request.Args[i].Type = "inplace"
		}
		request.Args[i].Value = arg.Value
		if arg.Key != "" {
			request.Args[i].Key = &arg.Key
		}
	}
	request.Static = rawReq.Static
	request.DoGetReturnValue = rawReq.DoGetReturnValue
	request.Property = rawReq.Property
	request.AssignedID = rawReq.AssignedID
}

func handler(conn net.Conn) {
	bufferedReader := bufio.NewReaderSize(conn, 16*1024)
	bufferedWriter := bufio.NewWriterSize(conn, 16*1024)
	var buffering = false
	for {
		requestLengthBinary, err := readNumBytes(bufferedReader, 4)
		if err != nil {
			return
		}
		reqLen := int(binary.LittleEndian.Uint32(requestLengthBinary))
		log.Println("Length: ", reqLen)

		tagBinary, err := readNumBytes(bufferedReader, 4) // TODO: Tag
		tag := int(binary.LittleEndian.Uint32(tagBinary))
		switch tag {
		case START_BUFFERING:
			log.Println("Buffering enabled")
			buffering = true
			continue
		case STOP_BUFFERING:
			log.Println("Buffering disabled")
			buffering = false
			bufferedWriter.Flush() // TODO
			continue
		case REQUEST:
			log.Println("Actual request")
		}

		payload, err := readNumBytes(bufferedReader, reqLen)
		log.Println("Payload: ", payload)

		var req Request
		readProtobuf(&req, payload)
		//err = json.Unmarshal(payload, &req)
		//if err != nil {
		//	log.Fatal("Unmarshal error: ", err)
		//}

		//response := process(req)
		//response := ChannelResponse{ReturnValue: 0}
		//
		//responseData, err := json.Marshal(response)
		//responseData := []byte("{\"return_value\": 0}")
		data := exchange.ChannelResponse_ReturnValueInt{ReturnValueInt: 0}
		resp := exchange.ChannelResponse{ReturnValue: &data}
		responseData, err := resp.Marshal()

		encoded := make([]byte, 4)
		binary.LittleEndian.PutUint32(encoded, uint32(len(responseData)))
		_, err = bufferedWriter.Write(encoded)

		binary.LittleEndian.PutUint32(encoded, uint32(1)) // TODO
		_, err = bufferedWriter.Write(encoded)

		_, err = bufferedWriter.Write(responseData)
		if err != nil {
			log.Fatal("Writing client error: ", err)
		}
		if !buffering {
			bufferedWriter.Flush()
		}
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
