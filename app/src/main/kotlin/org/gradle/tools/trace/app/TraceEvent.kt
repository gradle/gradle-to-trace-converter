package org.gradle.tools.trace.app

import com.google.gson.annotations.SerializedName

data class TraceEvent(

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("ph")
    val phaseType: String?,

    @SerializedName("ts")
    val timestamp: Long,

    @SerializedName("pid")
    val processId: Long? = 1L,

//    @SerializedName("id")
//    val id: Long?,

    @SerializedName("tid")
    val threadId: Long? = 1L,

    @SerializedName("cat")
    val categories: String? = "",

    @SerializedName("s")
    val scope: String? = null,

    @SerializedName("args")
    val arguments: Map<String?, Any?>? = null
)
        