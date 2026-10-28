package com.kharcha.tracker.core.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NO_NETWORK_MESSAGE = "Unable to reach the server. Check your connection."
private const val GENERIC_ERROR_MESSAGE = "Unexpected error"

/**
 * Runs a Retrofit call (endpoints declared with a [Response] return type) on
 * the IO dispatcher and maps the outcome into a [NetworkResult].
 *
 * Mapping rules:
 * - successful response -> [NetworkResult.Success] with the body
 * - error response      -> [NetworkResult.Error] with the HTTP status code and,
 *   when available, the server-provided message
 * - [IOException]       -> [NetworkResult.Error] with a "no connection" message
 * - anything else       -> [NetworkResult.Error] with the exception message
 *
 * Example:
 * ```
 * val result = safeCall { apiService.login(LoginRequest(email, password)) }
 * when (result) {
 *     is NetworkResult.Success -> ...
 *     is NetworkResult.Error -> ...
 * }
 * ```
 */
suspend fun <T> safeCall(block: suspend () -> Response<T>): NetworkResult<T> {
    val response: Response<T> = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: IOException) {
        return NetworkResult.Error(message = NO_NETWORK_MESSAGE)
    } catch (e: Exception) {
        return NetworkResult.Error(message = e.message ?: GENERIC_ERROR_MESSAGE)
    }

    val body = response.body()
    return if (response.isSuccessful && body != null) {
        NetworkResult.Success(body)
    } else {
        NetworkResult.Error(
            message = extractErrorMessage(response.errorBody()?.string(), response.code()),
            code = response.code()
        )
    }
}

/**
 * Runs an arbitrary suspend block on the IO dispatcher and maps any failure
 * into a [NetworkResult]. Use this for calls that return a plain value and
 * throw [HttpException] on failure.
 *
 * Mapping rules:
 * - [HttpException] -> [NetworkResult.Error] with the HTTP status code and,
 *   when available, the server-provided message
 * - [IOException]   -> [NetworkResult.Error] with a "no connection" message
 * - anything else   -> [NetworkResult.Error] with the exception message
 */
suspend fun <T> safeRun(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(withContext(Dispatchers.IO) { block() })
    } catch (e: HttpException) {
        val response = e.response()
        NetworkResult.Error(
            message = extractErrorMessage(response?.errorBody()?.string(), response?.code() ?: 0),
            code = response?.code()
        )
    } catch (e: IOException) {
        NetworkResult.Error(message = NO_NETWORK_MESSAGE)
    } catch (e: Exception) {
        NetworkResult.Error(message = e.message ?: GENERIC_ERROR_MESSAGE)
    }
}

/**
 * Extracts a human-readable message from an API error body.
 *
 * Handles FastAPI-style bodies such as `{"detail": "..."}` as well as the
 * common `{"message": "..."}` / `{"error": "..."}` shapes, and falls back to
 * the raw body text (or a generic message) when it cannot be parsed.
 */
internal fun extractErrorMessage(body: String?, code: Int): String {
    val raw = body?.takeIf { it.isNotBlank() } ?: return "Request failed ($code)"
    try {
        val json = JsonParser.parseString(raw)
        if (json is JsonObject) {
            (json.get("detail") ?: json.get("message") ?: json.get("error"))
                ?.takeIf { it.isJsonPrimitive }
                ?.let { return it.asString }
        }
    } catch (_: Exception) {
        // Not JSON - fall through to the raw body.
    }
    return raw
}
