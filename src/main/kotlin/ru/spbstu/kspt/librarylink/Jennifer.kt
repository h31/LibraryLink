package ru.spbstu.kspt.librarylink

import org.slf4j.LoggerFactory
import java.util.*

class Jennifer(private val exchange: ProcessDataExchange = LibraryLink.exchange) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(Jennifer::class.java)

    fun NewFile(packageName: String): File {
        val args = mutableListOf<Argument>(StringArgument(packageName))
        val peResponse = makeRequest(Request(import = "jennifer", objectID = "jennifer",
                args = args, methodName = "NewFile", doGetReturnValue = false))
        logger.info("Wrote get")
        val file = File(peResponse.assignedID)
        return file
    }

    inner class Group(private val storedName: String) : ru.spbstu.kspt.librarylink.Handle(storedName) {
        fun Func(): Statement {
            val response = makeRequest(Request(objectID = storedName, methodName = "Func",
                    doGetReturnValue = false, import = "", args = listOf()))
            val statement = Statement(response.assignedID)
            return statement
        }
    }

    inner class File(private val storedName: String) : ru.spbstu.kspt.librarylink.Handle(storedName) {
        fun ToGroup(): Group {
            val response = makeRequest(Request(objectID = storedName, methodName = "ToGroup",
                    doGetReturnValue = false, import = "", args = listOf()))
            val group = Group(response.assignedID)
            return group
        }

        fun GoString(): String {
            val response = makeRequest(Request(objectID = storedName, methodName = "GoString",
                    doGetReturnValue = true, import = "", args = listOf()))
            return response.returnValue as String
        }
    }

    inner class Statement(private val storedName: String) : ru.spbstu.kspt.librarylink.Handle(storedName) {
        fun Id(name: String): Statement {
            val args = mutableListOf<Argument>(StringArgument(name))
            val response = makeRequest(Request(objectID = storedName, methodName = "Id",
                    doGetReturnValue = false, import = "", args = args))
            val statement = Statement(response.assignedID)
            return statement
        }

        fun Params(): Statement {
            val response = makeRequest(Request(objectID = storedName, methodName = "Params",
                    doGetReturnValue = false, import = "", args = listOf()))
            val statement = Statement(response.assignedID)
            return statement
        }

        fun Block(): Statement {
            val response = makeRequest(Request(objectID = storedName, methodName = "Block",
                    doGetReturnValue = false, import = "", args = listOf()))
            val statement = Statement(response.assignedID)
            return statement
        }
    }
}