package org.gradle.tools.trace.app

interface BuildOperationConverter : BuildOperationVisitor {
    fun write()
}
