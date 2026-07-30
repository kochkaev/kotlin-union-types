package io.github.kochkaev.kotlin.uniontypes.build.maven

import io.github.kochkaev.kotlin.uniontypes.build.utils.LogFormatting
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.gradle.api.logging.Logger
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

class StagingPublisher(
    private val logger: Logger,
    private val stagingDir: File,
    private val bundleZipFile: File? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun publishToMaven(repo: MavenRepository) {
        val repoUrl = repo.url.orNull?.removeSuffix("/") ?: report("URL for repository ${repo.name} is not provided!")
        val user = repo.username.orNull ?: report("Username for ${repo.name} is not provided!")
        val pass = repo.password.orNull ?: report("Password for ${repo.name} is not provided!")

        val auth =
            if (repo.bearerAuth.getOrElse(false))
                "Bearer " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            else Credentials.basic(user, pass)

        if (repo.supportZipDeploymentPublish.getOrElse(false)) {
            val zipFile = bundleZipFile ?: report("Bundle zip file is not provided for Maven Central publication.")
            if (!zipFile.exists()) report("Zip file does not exist: ${zipFile.absolutePath}")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "bundle",
                    zipFile.name,
                    zipFile.asRequestBody("application/zip".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(repoUrl)
                .post(requestBody)
                .addHeader("Authorization", auth)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    report("Failed to upload to ${repo.name}: HTTP ${response.code} — $err")
                }
                logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ Successfully published to ${repo.name}!${LogFormatting.RESET}")
                logger.debug("Response body: ${response.body?.string()}")
            }
        } else {
            val filesToUpload = stagingDir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") }
                .toList()

            logger.lifecycle("${LogFormatting.BOLD}🔄 Found ${filesToUpload.size} files to publish to ${repo.name} ($repoUrl)${LogFormatting.RESET}")

            filesToUpload.forEach { file ->
                val relativePath = file.relativeTo(stagingDir).path.replace('\\', '/')
                val targetUrl = "$repoUrl/$relativePath"

                logger.lifecycle("${LogFormatting.BOLD}⏳ Uploading: $relativePath -> ${repo.name}${LogFormatting.RESET}")

                val request = Request.Builder()
                    .url(targetUrl)
                    .put(file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .addHeader("Authorization", auth)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string()
                        report("Failed to upload $relativePath to ${repo.name}: HTTP ${response.code} — $err")
                    }
                }
            }
            logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ Successfully published all artifacts to ${repo.name}!${LogFormatting.RESET}")
        }
    }

    private fun report(message: String): Nothing {
        logger.error("${LogFormatting.BOLD}${LogFormatting.RED}❌ $message${LogFormatting.RESET}")
        error(message)
    }
}