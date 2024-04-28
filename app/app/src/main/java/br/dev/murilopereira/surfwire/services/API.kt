package br.dev.murilopereira.surfwire.services

import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class TrafficStatus(val value: Float, val unit: String)
@JsonClass(generateAdapter = true)
data class Traffic(val received: TrafficStatus, val sent: TrafficStatus)
@JsonClass(generateAdapter = true)
data class Status(
    val client: String,
    val endpoint: String,
    val allowedIps: String,
    val uptime: String,
    val traffic: Traffic
)
@JsonClass(generateAdapter = true)
data class Toggle(
    val connected: Boolean
)
@JsonClass(generateAdapter = true)
data class Error(
    val messages: List<String>
)

interface SurfwireService {
    @GET("/status")
    fun status(): Call<Status>

    @POST("/connect")
    fun connect(): Call<Toggle>

    @POST("/disconnect")
    fun disconnect(): Call<Toggle>
}