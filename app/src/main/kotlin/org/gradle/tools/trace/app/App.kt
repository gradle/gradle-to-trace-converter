package org.gradle.tools.trace.app

import com.google.gson.Gson
import perfetto.protos.TraceOuterClass.Trace
import java.io.File

fun main(args: Array<String>) {
    val traceFile = File(args[0])
    val records = Gson().fromJson(traceFile.readText(), Array<BuildOperationRecord>::class.java)
    println("Read ${records.size} records from ${traceFile.name}")
    val traceEvents = TraceConverter().convert(records.toList())
    val trace = Trace.newBuilder()
        .addAllPacket(traceEvents)
        .build()
    val traceFileProto = File(traceFile.parentFile, traceFile.nameWithoutExtension + "-chrome.proto")
    traceFileProto.writeBytes(trace.toByteArray())
    println("Wrote ${traceEvents.size} events to ${traceFileProto.absolutePath}")
}
