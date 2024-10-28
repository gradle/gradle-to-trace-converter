package org.gradle.tools.trace.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildOperationVisitorTest {

    private val testTrace = BuildOperationLogs(
        logs = listOf(
            BuildOperationStart(1, "root1", startTime = 0),
            BuildOperationStart(2, "root1-child1", startTime = 10, parentId = 1),
            BuildOperationFinish(2, endTime = 20),
            BuildOperationStart(3, "root1-child2", startTime = 30, parentId = 1),
            BuildOperationFinish(3, endTime = 40),
            BuildOperationFinish(1, endTime = 100),
            BuildOperationStart(4, "root2", startTime = 150),
            BuildOperationStart(5, "root2-child1", startTime = 151, parentId = 4),
            BuildOperationStart(6, "root2-child1-child1", startTime = 151, parentId = 5),
            BuildOperationFinish(6, endTime = 153),
            BuildOperationStart(7, "root2-child1-child2", startTime = 155, parentId = 5),
            BuildOperationFinish(7, endTime = 156),
            BuildOperationFinish(5, endTime = 156),
            BuildOperationStart(8, "root2-child2", startTime = 157, parentId = 4),
            BuildOperationFinish(8, endTime = 158),
            BuildOperationFinish(4, endTime = 160),
        )
    )

    private fun collectVisitedIds(traversal: BuildOperationLogs): List<Int> {
        return buildList {
            BuildOperationVisitor.visitLogs(traversal, object : BuildOperationVisitor {
                override fun visit(start: BuildOperationStart): PostVisit {
                    add(start.id.toInt())
                    return { _, _ -> }
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
