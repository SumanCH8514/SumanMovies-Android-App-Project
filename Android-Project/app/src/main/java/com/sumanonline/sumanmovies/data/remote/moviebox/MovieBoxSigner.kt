package com.sumanonline.sumanmovies.data.remote.moviebox

import android.net.Uri
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object MovieBoxSigner {
    private val DEFAULT_SECRET_BYTES = byteArrayOf(
        0xef.toByte(), 0xa8.toByte(), 0x91.toByte(), 0x97.toByte(), 0x4e.toByte(),
        0xec.toByte(), 0xd3.toByte(), 0x14.toByte(), 0x8d.toByte(), 0xf6.toByte(),
        0x3a.toByte(), 0xa6.toByte(), 0x11.toByte(), 0x60.toByte(), 0x2d.toByte(),
        0xef.toByte(), 0xd1.toByte(), 0x01.toByte(), 0x25.toByte(), 0x9b.toByte(),
        0xa5.toByte(), 0x21.toByte(), 0x02.toByte(), 0x2c.toByte(), 0x57.toByte(),
        0xae.toByte(), 0x05.toByte(), 0x66.toByte(), 0xbd.toByte(), 0x8e.toByte()
    )

    private const val STOCK_CERT_SHA256 = "03d4b741632d136bc23e2de4c6adcdb7712f1980b619c183221801730bbd0410"
    private const val BUILD_TAG_SALT = "oneroom"
    private const val SIGNATURE_BODY_MAX_BYTES = 102400

    fun md5Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun md5Hex(str: String): String {
        return md5Hex(str.toByteArray(StandardCharsets.UTF_8))
    }

    fun hmacMd5Base64(key: ByteArray, message: String): String {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(key, "HmacMD5"))
        val signed = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signed, Base64.NO_WRAP)
    }

    fun generateClientToken(ts: Long): String {
        val tsStr = ts.toString()
        val reversed = tsStr.reversed()
        val hash = md5Hex(reversed)
        return "$tsStr,$hash"
    }

    fun generateClientBuild(ts: Long): String {
        val rand = Random.nextInt(1000)
        val stamp = "${ts / 1000}${String.format(java.util.Locale.US, "%03d", ts % 1000)}000$rand"
        val hash = md5Hex("$STOCK_CERT_SHA256$BUILD_TAG_SALT$stamp")
        return "$stamp.$hash"
    }

    fun buildCanonicalString(
        method: String,
        accept: String,
        contentType: String,
        urlStr: String,
        body: String?,
        timestampMs: Long
    ): String {
        val uri = Uri.parse(urlStr)
        val path = uri.path ?: ""
        val sortedQuery = uri.queryParameterNames.sorted().joinToString("&") { name ->
            val value = uri.getQueryParameter(name) ?: ""
            "$name=$value"
        }
        val canonicalUrl = if (sortedQuery.isNotEmpty()) "$path?$sortedQuery" else path

        val bodyLength: String
        val bodyHash: String
        if (body != null) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val len = bytes.size
            val truncated = if (len > SIGNATURE_BODY_MAX_BYTES) bytes.copyOfRange(0, SIGNATURE_BODY_MAX_BYTES) else bytes
            bodyHash = md5Hex(truncated)
            bodyLength = len.toString()
        } else {
            bodyLength = ""
            bodyHash = ""
        }

        return "${method.uppercase()}\n$accept\n$contentType\n$bodyLength\n$timestampMs\n$bodyHash\n$canonicalUrl"
    }

    fun generateSignature(
        method: String,
        accept: String,
        contentType: String,
        urlStr: String,
        body: String?,
        timestampMs: Long
    ): String {
        val canonical = buildCanonicalString(method, accept, contentType, urlStr, body, timestampMs)
        val sigB64 = hmacMd5Base64(DEFAULT_SECRET_BYTES, canonical)
        return "$timestampMs|2|$sigB64"
    }

    @Volatile
    var serverTimeOffsetMs: Long = 0L

    fun currentTimestamp(): Long = System.currentTimeMillis() + serverTimeOffsetMs

    fun buildSignedHeaders(
        method: String,
        urlStr: String,
        body: String?,
        authToken: String?,
        overrideTs: Long? = null
    ): Map<String, String> {
        val ts = overrideTs ?: currentTimestamp()
        val accept = "application/json"
        val contentType = "application/json"

        val clientToken = generateClientToken(ts)
        val signature = generateSignature(method, accept, contentType, urlStr, body, ts)
        val buildTag = generateClientBuild(ts)

        val clientInfo = """
            {"package_name":"com.community.oneroom","version_name":"4.0.01.0813.03","version_code":50020120,"os":"android","os_version":"12","install_ch":"ps","device_id":"850cb21e54f003a78223b03b7eb46f5c","install_store":"ps","gaid":"42a97f5a-f9f9-77dc-c951-2bfebe2d2ac1","brand":"Redmi","model":"22101316G","system_language":"en","net":"NETWORK_WIFI","region":"US","timezone":"Asia/Kolkata","sp_code":"40401","X-Play-Mode":"2","X-Client-Build":"$buildTag"}
        """.trimIndent().replace("\n", "")

        val headers = mutableMapOf(
            "User-Agent" to "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G; Build/QP1A.191005.007.A3; Cronet/135.0.7012.3)",
            "Accept" to accept,
            "Content-Type" to contentType,
            "Connection" to "keep-alive",
            "x-client-token" to clientToken,
            "x-tr-signature" to signature,
            "x-client-build" to buildTag,
            "x-client-info" to clientInfo,
            "x-client-status" to "0",
            "X-Play-Mode" to "2",
            "x-forwarded-for" to "103.241.${Random.nextInt(1, 254)}.${Random.nextInt(1, 254)}"
        )

        if (authToken != null) {
            headers["Authorization"] = "Bearer $authToken"
        }

        return headers
    }
}
