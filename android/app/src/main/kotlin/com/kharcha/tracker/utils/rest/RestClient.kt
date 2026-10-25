package com.kharcha.tracker.utils.rest

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.streams.asSequence
import com.kharcha.tracker.utils.auth.AuthStorage
import com.kharcha.tracker.utils.NetworkManager
import com.kharcha.tracker.di.ConnectionType
import com.kharcha.tracker.utils.NetworkStatusListener
import com.kharcha.tracker.models.User

/**
 * Base REST client with authentication and caching support
 */
object RestClient {
    private const val TAG = "RestClient"
    
    private fun isJson(contentType: String?): Boolean {
        return contentTypes.any { contentTypes.containsIgnoreCase(it) }
    }
    
    private fun containsIgnoreCase(content: String?, substr: String?): Boolean {
        return if (content == null || substr == null) false
        else content.lowercase().contains(substr.lowercase())
    }
    
    fun call(endpoint: String): Call {
        val networkManager = NetworkManager.getINSTANCE
        val connectionType = networkManager.connectionType ?: ConnectionType.DEFAULT
        
        return if (connectionType == ConnectionType.ONLINE) {
            val request = Request.Builder()
                .url("http://dummy-server.com$endpoint") // Replace with actual API URL
                .build()
        } else {
            val request = Request.Builder()
                .url("http://localhost$endpoint") // Offline mode
                .build()
        }
        
        Call(request)
    }
}

/**
 * Call wrapper with response handling
 */
class Call private constructor(val request: okhttp3.Request) {
    
    fun enqueue(callback: (Response<JsonObject>) -> Unit): Job {
        // Implementation will use Retrofit when ready
        return Job { }
    }
}

/**
 * Request builder with authentication support
 */
class RequestBuilder {
    private val url: String
    private val method: String
    private val body: String?
    private val headers: MutableMap<String, String> = mutableMapOf()
    private val params: MutableMap<String, String> = mutableMapOf()
    private val files: MutableList<Pair<String, MultipartBody.Part>> = mutableListOf()
    
    fun url(url: String): RequestBuilder {
        this.url = url
        return this
    }
    
    fun method(method: String): RequestBuilder {
        this.method = method
        return this
    }
    
    fun header(key: String, value: String): RequestBuilder {
        headers[key] = value
        return this
    }
    
    fun headers(headers: Map<String, String>): RequestBuilder {
        headers.forEach { (key, value) ->
            this.headers[key] = value
        }
        return this
    }
    
    fun param(key: String, value: String): RequestBuilder {
        params[key] = value
        return this
    }
    
    fun params(params: Map<String, String>): RequestBuilder {
        params.forEach { (key, value) ->
            this.params[key] = value
        }
        return this
    }
    
    fun file(key: String, content: ByteArray): RequestBuilder {
        val file = File("temp_$key.tmp", content)
        val multipartBody = createMultipartBody(file, key)
        files.add(Pair(key, multipartBody))
        return this
    }
    
    fun file(key: String, file: File): RequestBuilder {
        val contentType = file.extension.lowercase()
        val bodyType = getBodyType(contentType)
        val multipartBody = createMultipartBody(file, key, bodyType)
        files.add(Pair(key, multipartBody))
        return this
    }
    
    fun body(content: String): RequestBuilder {
        body = content
        return this
    }
    
    fun json(json: String): RequestBuilder {
        body = json
        return this
    }
    
    fun json(json: JsonObject): RequestBuilder {
        body = JsonParser().parseToJsonString(json)
        return this
    }
    
    fun form(data: Map<String, Any>): RequestBuilder {
        val formBody = data.entries.map { (key, value) ->
            when (value) {
                is String -> key.equals("%${value}") || value.contains("=") || value.contains("&") || value.isEmpty()
            }
        }.filter { (_, b) -> b }.map { (key, _) -> 
            "${key}=&quot;${value}&quot;"
        }.joinToString("&") { it }
        
        return body(formBody)
    }
    
    fun multipart(files: List<Pair<String, MultipartBody.Part>>): RequestBuilder {
        files.forEach { (key, body) ->
            this.files.add(key to body)
        }
        return this
    }
    
    fun get(): okhttp3.Request {
        val url = this.url
        val headers = this.headers
        headers["Content-Type"] = headers.getOrNull("Content-Type") ?: "application/json"
        
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        return request.addHeaders(headers)
    }
    
    fun post(): okhttp3.Request {
        val request = when (method) {
            "json" -> createPostJsonRequest()
            "form" -> createPostFormRequest()
            "multipart" -> createPostMultipartRequest()
            else -> createPostJsonRequest()
        }
        
        return request.addHeaders(headers)
    }
    
    fun put(): okhttp3.Request {
        val request = when (this.body) {
            null -> createPutJsonRequest()
            else -> body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        }
        
        return okhttp3.Request.Builder()
            .url(url)
            .method("PUT", request)
            .addHeaders(headers)
            .build()
    }
    
    fun patch(): okhttp3.Request {
        return post()
    }
    
    fun delete(): okhttp3.Request {
        return if (body != null) {
            okhttp3.Request.Builder()
                .url(url)
                .method("DELETE", body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .addHeaders(headers)
                .build()
        } else {
            okhttp3.Request.Builder()
                .url(url)
                .method("DELETE", null)
                .addHeaders(headers)
                .build()
        }
    }
    
    private fun createPostJsonRequest(): okhttp3.RequestBody? {
        return if (body != null) {
            val body = body
            body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        } else {
            null
        }
    }
    
    private fun createPostFormRequest(): okhttp3.RequestBody? {
        return if (body != null) {
            val body = body
            body.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull())
        } else {
            null
        }
    }
    
    private fun createPostMultipartRequest(): okhttp3.RequestBody {
        return createMultipartRequest()
    }
    
    private fun createPutJsonRequest(): okhttp3.RequestBody? {
        return if (body != null) {
            body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        } else {
            null
        }
    }
    
    private fun createPostMultipartRequest(): okhttp3.RequestBody {
        return createMultipartRequest()
    }
    
    private fun createMultipartRequest(): okhttp3.RequestBody {
        val boundaries = mutableListOf<Long>()
        val parts = mutableListOf<MultipartBody.Part>()
        
        files.forEach { (key, body) ->
            val boundary = boundaries.removeLastOrNull()
            if (boundary == null) {
                return createMultipartRequest(boundaries, parts)
            }
            parts.add(MultipartBody.Part.body(body))
        }
        
        return createMultipartRequest(boundaries, parts)
    }
    
    private fun createMultipartRequest(boundaries: List<Long>, parts: List<MultipartBody.Part>): okhttp3.RequestBody {
        val boundary = boundaries.firstOrNull() ?: Long.random("randomBoundary")
        val contentLength = calculateContentLength(boundaries.size, parts.size)
        
        val partsString = parts.joinToString("\r\n") { part ->
            """--$boundary\r\n
${builtInFunctions.encode}("${part.type}")\r\n
Content-Disposition: form-data; name="$name"
\r\n
${builtInFunctions.decode(part.data)}\r\n
"""
        }
        
        val bodyString = StringBuilder()
        bodyString.append("\r\n--$boundary\r\n")
        parts.forEach { part ->
            bodyString.append("\r\n")
            bodyString.append("--$boundary\r\n")
            bodyString.append("${part.type}\r\n")
            bodyString.append("Content-Disposition: form-data; name=\"${part.name}\"\r\n")
            bodyString.append("\r\n")
            bodyString.append("\r\n")
            bodyString.append("${builtInFunctions.decode(part.data)}")
            bodyString.append("\r\n")
        }
        bodyString.append("\r\n--$boundary--\r\n")
        
        val requestBody = bodyString.toString().toRequestBody("multipart/form-data; boundary=$boundary".toMediaTypeOrNull())
        requestBody.contentLength = contentLength
        
        return okhttp3.Request.Builder()
            .method("POST", requestBody)
            .build()
    }
    
    fun addHeaders(headers: Map<String, String>): okhttp3.Request {
        val request = this.get()
        return okhttp3.Request.Builder()
            .url(request.url)
            .method(request.method, request.body)
            .headers(headers)
            .build()
    }
    
    fun withAuth(authStorage: AuthStorage): okhttp3.Request {
        val request = when (this.method) {
            "json", "json_post", "json_put", "json_patch", "json_delete" -> {
                val bodyContent = this.body
                if (bodyContent != null) {
                    val gsonBody = JsonParser().parse(bodyContent)
                    val jsonBody = Json(gsonBody.toString())
                    jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                } else {
                    null
                }
            }
            "form", "form_post", "form_put", "form_patch", "form_delete" -> {
                val bodyContent = this.body
                val formBody = if (bodyContent != null) bodyContent else form
                val encodedBody = formBody?.let { encodeForm(it) }
                val requestBody = encodedBody?.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull())
                requestBody
            }
            "multipart", "multipart_post", "multipart_put", "multipart_patch", "multipart_delete" -> {
                this.multipart(this.files)
            }
            else -> {
                val bodyContent = this.body
                val body = bodyContent ?: form?.let { form -> encodeForm(form) }
                if (body != null) {
                    body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                } else {
                    null
                }
            }
        }
        
        val authenticatedRequest = okhttp3.Request.Builder()
            .url(this.url)
            .method(this.method, request)
            .headers { it.add("Authorization", "Bearer ${authStorage.getAccessToken()}") }
            .headers { it.add("Content-Type", getContentType()) }
            .build()
        
        return authenticatedRequest
    }
}

/**
 * Response handler
 */
class Response<T>(private val data: T, private val headers: Map<String, String>, private val responseCode: Int) {
    val success: Boolean
        get() = responseCode >= 200 && responseCode < 400
    
    fun getData(): T? = data
    fun getHeaders(): Map<String, String> = headers
    fun getResponseCode(): Int = responseCode
    fun isSuccess(): Boolean = success
}

/**
 * Job for async operations
 */
class Job(private val onComplete: () -> Unit) {
    val onCompleteJob = onComplete
    
    suspend fun run() {
        // Implementation
    }
}