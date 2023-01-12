package org.gradle.tools.trace.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildOperationVisitorTest {

    private val testTrace = BuildOperationTraceSlice(
        records = listOf(
            BuildOperationRecord(1, "root1", startTime = 0, endTime = 100, children = listOf(
                BuildOperationRecord(2, "root1-child1", startTime = 10, endTime = 20),
                BuildOperationRecord(3, "root1-child2", startTime = 30, endTime = 40),
            )),
            BuildOperationRecord(4, "root2", startTime = 150, endTime = 160, children = listOf(
                BuildOperationRecord(5, "root2-child1", startTime = 151, endTime = 156, children = listOf(
                    BuildOperationRecord(6, "root2-child1-child1", startTime = 151, endTime = 153),
                    BuildOperationRecord(7, "root2-child1-child2", startTime = 155, endTime = 156),
                )),
                BuildOperationRecord(8, "root2-child2", startTime = 157, endTime = 158),
            )),
        )
    )

    private fun collectVisitedIds(traversal: BuildOperationTraceSlice): List<Int> {
        return buildList {
            BuildOperationVisitor.visitRecords(traversal, object : BuildOperationVisitor {
                override fun visit(record: BuildOperationRecord): PostVisit {
                    add(record.id.toInt())
                    return {}
                }
            })
        }
    }

    @Test
    fun `build operation trace is traversed`() {
        val actualTraversedIds = collectVisitedIds(testTrace)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), actualTraversedIds)
    }

    @Test
    fun `build operation trace is filtered on traversal`() {
        val testTrace = testTrace.copy(
            include = Regex("^root1$|^root2-child1$"),
            exclude = Regex("^root2$|^root2-child1-child1$"),
        )
        val actualTraversedIds = collectVisitedIds(testTrace)

        // Should not exclude "root2" subtree, because only its descendants are included
        assertEquals(listOf(1, 2, 3, 5, 7), actualTraversedIds)
    }
}
