package kr.co.nuni.gildam.sync.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kr.co.nuni.gildam.auth.KeystoreTokenStore
import kr.co.nuni.gildam.auth.NuniAuthManager
import okhttp3.Authenticator
import kr.co.nuni.gildam.sync.api.GildamOvhApi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    // TODO(Open Question #7): OVH 서버의 실제 길담 API 서브도메인 확정 시 변경
    /** knhanul/gildam-api 운영 주소 (끝의 / 필수) */
    private const val OVH_BASE_URL = "https://www.nuni.co.kr/gildam-api/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenStore: KeystoreTokenStore,
        authManager: NuniAuthManager
    ): OkHttpClient {
        // 401 이면 refresh_token 으로 한 번 갱신(서버가 refresh 회전) 후 재시도, 실패 시 재로그인 배너
        val refreshAuthenticator = Authenticator { _, response ->
            if (response.priorResponse != null) return@Authenticator null
            val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
            val fresh = authManager.refreshAccessTokenBlocking(stale) ?: return@Authenticator null
            response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }

        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
            tokenStore.getAccessToken()?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        // TrackPoints 대량 전송 시 Gzip 압축을 수행하는 인터셉터
        val gzipInterceptor = Interceptor { chain ->
            val original = chain.request()
            val body = original.body
            if (body == null || original.header("X-Gzip-Compress") != "true") {
                return@Interceptor chain.proceed(original)
            }
            val compressedRequest = original.newBuilder()
                .removeHeader("X-Gzip-Compress")
                .header("Content-Encoding", "gzip")
                .method(original.method, gzipBody(body))
                .build()
            chain.proceed(compressedRequest)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(refreshAuthenticator)
            .addInterceptor(gzipInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGildamOvhApi(
        okHttpClient: OkHttpClient,
        json: Json
    ): GildamOvhApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(OVH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GildamOvhApi::class.java)
    }

    private fun gzipBody(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength(): Long = -1L
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }
}
