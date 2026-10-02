package com.example.estante.data.gutenberg

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/* ---------------------------------------------------------------------------
 * Modelos da API Gutendex (https://gutendex.com), um catálogo gratuito e sem
 * chave de API para os livros do Project Gutenberg.
 * --------------------------------------------------------------------------- */

@Serializable
data class GutendexResponse(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GutendexBook> = emptyList()
)

@Serializable
data class GutendexBook(
    val id: Long,
    val title: String = "",
    val authors: List<GutendexAuthor> = emptyList(),
    val formats: Map<String, String?> = emptyMap()
) {
    val pdfUrl: String? get() = formats["application/pdf"]
    val coverUrl: String? get() = formats["image/jpeg"]
}

@Serializable
data class GutendexAuthor(
    val name: String = "",
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("death_year") val deathYear: Int? = null
)

/** Formata "Austen, Jane, 1775-1817" como "Jane Austen". */
fun GutendexBook.displayAuthor(): String =
    authors.joinToString(", ") { author -> formatAuthorName(author.name) }

private fun formatAuthorName(raw: String): String {
    val yearRegex = Regex("""^\d{4}(-\d{0,4})?$""")
    val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val names = parts.filterNot { yearRegex.matches(it) }
    return when {
        names.size >= 2 -> names.drop(1).joinToString(" ") + " " + names.first()
        names.size == 1 -> names.first()
        else -> raw
    }
}

/* ---------------------------------------------------------------------------
 * Serviço Retrofit
 * --------------------------------------------------------------------------- */

interface GutendexApi {

    @GET("books")
    suspend fun list(
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("mime_type") mimeType: String = "application/pdf"
    ): GutendexResponse

    @GET
    suspend fun byUrl(@Url url: String): GutendexResponse
}

/* ---------------------------------------------------------------------------
 * Cliente HTTP compartilhado
 * --------------------------------------------------------------------------- */

object GutenbergNetwork {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val api: GutendexApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://gutendex.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GutendexApi::class.java)
    }

    /**
     * Baixa um arquivo em [dest], notificando o progresso.
     * Progresso: 0f..1f, ou -1f quando o tamanho total é desconhecido.
     */
    suspend fun downloadToFile(url: String, dest: File, onProgress: (Float) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Resposta vazia")
                val totalBytes = body.contentLength()
                dest.parentFile?.mkdirs()
                if (totalBytes <= 0) onProgress(-1f)
                dest.outputStream().use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) onProgress(downloaded.toFloat() / totalBytes)
                    }
                    out.flush()
                }
                if (totalBytes <= 0) onProgress(1f)
            }
        }
    }

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Estante/1.0"
}
