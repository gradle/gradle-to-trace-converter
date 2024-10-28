package org.gradle.tools.trace.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import kotlin.streams.toList

fun main(args: Array<String>) = ConverterApp().main(args)

enum class OutputFormat(val filePostfix: String) {
    ALL(""),
    CHROME_TRACE("-chrome.proto"),
    TIMELINE("-timeline.csv");
}

class ConverterApp : CliktCommand() {

    private val buildOperationTrace: File by argument(name = "trace", help = "Path to the build operation trace file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val outputFormat: OutputFormat by option("-o", "--output-format", help = "The output format to use")
        .choice(
            "all" to OutputFormat.ALL,
            "chrome" to OutputFormat.CHROME_TRACE,
            "timeline" to OutputFormat.TIMELINE,
        )
        .default(OutputFormat.ALL)

    private val include: Regex? by option("-i", "--include", help = "Regex to filter the build operations to include by display name")
        .convert { it.toRegex() }

    private val exclude: Regex? by option("-e", "--exclude", help = "Regex to filter the build operations to exclude")
        .convert { it.toRegex() }

    override fun run() {
        val logs = readLogs(buildOperationTrace)
        val formats = when (outputFormat) {
            OutputFormat.ALL -> OutputFormat.values().filter { it != OutputFormat.ALL }
            else -> listOf(outputFormat)
        }

        for (outputFormat in formats) {
            val outputFile = outputFile(buildOperationTrace, outputFormat)
            when (outputFormat) {
                OutputFormat.CHROME_TRACE -> convertToChromeTrace(outputFile, logs)
                OutputFormat.TIMELINE -> convertToTimelineCsv(outputFile, logs)
                else -> error("Unexpected output format: $outputFormat")
            }
        }
    }

    private fun outputFile(traceFile: File, format: OutputFormat): File {
        return File(traceFile.parentFile, traceFile.nameWithoutExtension + format.filePostfix)
    }

    private fun convertToChromeTrace(outputFile: File, logs: BuildOperationLogs) {
        TraceToChromeTraceConverter().convert(logs, outputFile)
    }

    private fun convertToTimelineCsv(outputFile: File, logs: BuildOperationLogs) {
        TraceToTimelineConverter().convert(logs, outputFile)
    }

    private fun readLogs(traceFile: File): BuildOperationLogs {
        val logs = readBuildOperationLogs(traceFile)
        println("Read ${logs.size} build operation logs from ${traceFile.name}")
        return BuildOperationLogs(logs, include, exclude)
    }

    private fun readBuildOperationLogs(traceLogFile: File): List<BuildOperationLog> {
        try {
            val gson = Gson().newBuilder()
                .registerTypeAdapterFactory(BuildOperationLogAdapterFactory())
                .create()
            Files.lines(traceLogFile.toPath()).use { lines ->
                return lines
                    .map { line -> gson.fromJson(line, BuildOperationLog::class.java) }
                    .toList()
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read build operation trace file: ${traceLogFile.absolutePath}", e)
        }
    }
}
