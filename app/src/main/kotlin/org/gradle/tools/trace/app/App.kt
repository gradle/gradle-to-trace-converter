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
import java.util.stream.Stream

fun main(args: Array<String>) = ConverterApp().main(args)

enum class OutputFormat(val filePostfix: String) {
    ALL(""),
    CHROME_TRACE("-chrome.proto"),
    TIMELINE("-timeline.csv");
}

class ConverterApp : CliktCommand(name = "gradle-trace-converter") {

    private val buildOperationTrace: File by argument(
        name = "trace",
        help = "Path to the build operation trace file (ends with -log.txt)"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val outputFormat: OutputFormat by option("-o", "--output-format", help = "The output format to use")
        .choice(
            "all" to OutputFormat.ALL,
            "chrome" to OutputFormat.CHROME_TRACE,
            "timeline" to OutputFormat.TIMELINE,
        )
        .default(OutputFormat.CHROME_TRACE)

    private val include: Regex? by option(
        "-i",
        "--include",
        help = "Regex to filter the build operations to include by display name"
    ).convert { it.toRegex() }

    private val exclude: Regex? by option(
        "-e",
        "--exclude",
        help = "Regex to filter the build operations to exclude"
    ).convert { it.toRegex() }

    override fun run() {
        val formats = when (outputFormat) {
            OutputFormat.ALL -> OutputFormat.entries.filter { it != OutputFormat.ALL }
            else -> listOf(outputFormat)
        }
        val converters = formats.map { format ->
            val outputFile = outputFile(buildOperationTrace, format)
            when (format) {
                OutputFormat.CHROME_TRACE -> toChromeTraceConverter(outputFile)
                OutputFormat.TIMELINE -> toTimelineCsvConverter(outputFile)
                else -> error("Unexpected output format: $format")
            }
        }
        val compositeVisitor = CompositeBuildOperationVisitor(converters)
        readLogs(buildOperationTrace).use { logs ->
            BuildOperationVisitor.visitLogs(logs, compositeVisitor)
            converters.forEach { it.write() }
        }
    }

    private fun outputFile(traceFile: File, format: OutputFormat): File {
        val baseName = traceFile.nameWithoutExtension
            .removeSuffix("-log") // always appended by Gradle, when writing a build operations log
        return File(traceFile.parentFile, baseName + format.filePostfix)
    }

    private fun toChromeTraceConverter(outputFile: File): TraceToChromeTraceConverter =
        TraceToChromeTraceConverter(outputFile)

    private fun toTimelineCsvConverter(outputFile: File): TraceToTimelineConverter =
        TraceToTimelineConverter(outputFile)

    private fun readLogs(traceFile: File): BuildOperationLogs {
        val logs = readBuildOperationLogs(traceFile)
        println("Read build operation logs from ${traceFile.name}")
        return BuildOperationLogs(logs, include, exclude)
    }

    private fun readBuildOperationLogs(traceLogFile: File): Stream<BuildOperationLog> {
        try {
            val gson = Gson().newBuilder()
                .registerTypeAdapterFactory(BuildOperationLogAdapterFactory())
                .create()
            return Files.lines(traceLogFile.toPath())
                .map { line -> gson.fromJson(line, BuildOperationLog::class.java) }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read build operation trace file: ${traceLogFile.absolutePath}", e)
        }
    }
}

class CompositeBuildOperationVisitor(private val delegates: List<BuildOperationVisitor>) : BuildOperationVisitor {

    override fun visit(start: BuildOperationStart): PostVisit {
        val postVisits = delegates.map { it.visit(start) }
        return { startOp, finishOp ->
            postVisits.forEach { it.invoke(startOp, finishOp) }
        }
    }

}
