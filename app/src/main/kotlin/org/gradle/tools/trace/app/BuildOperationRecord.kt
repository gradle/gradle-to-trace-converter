/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.tools.trace.app

class BuildOperationRecord(
    val id: Long,
    val parentId: Long,
    val displayName: String,
    val startTime: Long,
    val endTime: Long,
    val workerLeaseNumber: Int?,
    val threadDescription: String?,
    details: Map<String, *>?,
    val detailsClassName: String?,
    result: Map<String, *>?,
    val resultClassName: String?,
    val failure: String,
    val progress: List<Progress>?,
    val children: List<BuildOperationRecord>?
) {
    val details: Map<String, Any?>? = details?.toMap()
    val result: Map<String, Any?>? = result?.toMap()

    override fun toString(): String {
        return "BuildOperationRecord{$id->$displayName}"
    }

    class Progress(
        val time: Long,
        details: Map<String, Any?>?,
        val detailsClassName: String?
    ) {
        val details: Map<String, Any?>? = details?.toMap()

        override fun toString(): String {
            return "Progress{details=$details, detailsClassName='$detailsClassName'}"
        }
    }
}
