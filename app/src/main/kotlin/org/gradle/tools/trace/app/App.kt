package org.gradle.tools.trace.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import perfetto.protos.TraceOuterClass.Trace
import java.io.File

fun main(args: Array<String>) = ConverterApp().main(args)

class ConverterApp : CliktCommand() {

    private val buildOperationTrace by argument(name = "trace", help = "Path to the build operation trace file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    override fun run() {
        convertToChromeTrace(buildOperationTrace)
    }

    private fun convertToChromeTrace(traceFile: File) {
        val inputTraceJsonText = traceFile.readText()
        val records = Gson().fromJson(inputTraceJsonText, Array<BuildOperationRecord>::class.java)
        println("Read ${records.size} records from ${traceFile.name}")
        val traceEvents = TraceConverter().convert(records.toList())
        val trace = Trace.newBuilder()
            .addAllPacket(traceEvents)
            .build()
        val traceFileProto = File(traceFile.parentFile, traceFile.nameWithoutExtension + "-chrome.proto")
        traceFileProto.writeBytes(trace.toByteArray())
        println("Wrote ${traceEvents.size} events to ${traceFileProto.absolutePath}")
    }

}
