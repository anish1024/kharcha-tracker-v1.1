package com.kharcha.tracker.core.util

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SafeCallTest {

    @Test
    fun success_returnsData() = runBlocking {
        val result = safeRun<String> { "hello" }
        assertTrue(result is NetworkResult.Success)
        assertEquals("hello", (result as NetworkResult.Success).data)
    }

    @Test
    fun ioException_mapsToConnectionError() = runBlocking {
        val result = safeRun<String> { throw java.io.IOException("timeout") }
        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals("Unable to reach the server. Check your connection.", error.message)
        assertEquals(null, error.code)
    }

    @Test
    fun httpException_includesStatusCodeAndServerMessage() = runBlocking {
        val body = """{"detail":"Invalid credentials"}""".toResponseBody("application/json".toMediaType())
        val exception = HttpException(Response.error<String>(401, body))
        val result = safeRun<String> { throw exception }
        val error = result as NetworkResult.Error
        assertEquals(401, error.code)
        assertEquals("Invalid credentials", error.message)
    }

    @Test
    fun unknownException_usesExceptionMessage() = runBlocking {
        val result = safeRun<String> { throw IllegalStateException("boom") }
        val error = result as NetworkResult.Error
        assertEquals("boom", error.message)
    }

    @Test
    fun responseSuccess_returnsBody() = runBlocking {
        val response = Response.success("token")
        val result = safeCall { response }
        assertTrue(result is NetworkResult.Success)
        assertEquals("token", (result as NetworkResult.Success).data)
    }

    @Test
    fun responseError_mapsFastApiDetailAndCode() = runBlocking {
        val body = """{"detail":"Invalid credentials"}""".toResponseBody("application/json".toMediaType())
        val response = Response.error<String>(401, body)
        val result = safeCall { response }
        val error = result as NetworkResult.Error
        assertEquals(401, error.code)
        assertEquals("Invalid credentials", error.message)
    }

    @Test
    fun responseError_nonJsonBody_fallsBackToRawText() = runBlocking {
        val body = "Bad Gateway".toResponseBody("text/plain".toMediaType())
        val response = Response.error<String>(502, body)
        val result = safeCall { response }
        val error = result as NetworkResult.Error
        assertEquals(502, error.code)
        assertEquals("Bad Gateway", error.message)
    }

    @Test
    fun responseError_emptyBody_fallsBackToGenericMessage() = runBlocking {
        val body = "".toResponseBody("application/json".toMediaType())
        val response = Response.error<String>(500, body)
        val result = safeCall { response }
        val error = result as NetworkResult.Error
        assertEquals(500, error.code)
        assertEquals("Request failed (500)", error.message)
    }
}
