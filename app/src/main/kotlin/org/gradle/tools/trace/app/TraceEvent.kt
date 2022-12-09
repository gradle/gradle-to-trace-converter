package org.gradle.tools.trace.app

import com.google.gson.annotations.SerializedName

data class TraceEvent(

    @get:SerializedName("name")
    val name: String? = null,

    @get:SerializedName("ph")
    val phaseType: String?,

    @get:SerializedName("ts")
    val timestamp: Long,

    @get:SerializedName("pid")
    val processId: Long? = 1L,

//    @get:SerializedName("id")
//    val id: Long?,

    @get:SerializedName("tid")
    val threadId: Long? = 1L,

    @get:SerializedName("cat")
    val categories: String? = "",

    @get:SerializedName("s")
    val scope: String? = null,

    @get:SerializedName("args")
    val arguments: Map<String?, Any?>? = null
)
        