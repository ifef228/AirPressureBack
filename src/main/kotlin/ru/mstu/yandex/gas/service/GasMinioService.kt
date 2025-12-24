package ru.mstu.yandex.gas.service

import io.minio.*
import io.minio.http.Method
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class GasMinioService(
    @Value("\${minio.endpoint:http://localhost:9000}")
    private val endpoint: String,

    @Value("\${minio.access-key:minioadmin}")
    private val accessKey: String,

    @Value("\${minio.secret-key:minioadmin}")
    private val secretKey: String,

    @Value("\${minio.bucket-name:gas-images}")
    private val bucketName: String
) {

    private val minioClient: MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()

    init {
        createBucketIfNotExists()
    }

    private fun createBucketIfNotExists() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
                println("MinioService: Bucket '$bucketName' created successfully")
            }
        } catch (e: Exception) {
            println("MinioService: Error creating bucket: ${e.message}")
        }
    }

    /**
     * Загружает изображение в Minio
     */
    fun uploadImage(file: MultipartFile, gasId: Long): String {
        try {
            val fileName = generateFileName(gasId, file.originalFilename ?: "image")

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(fileName)
                    .stream(file.inputStream, file.size, -1)
                    .contentType(file.contentType ?: "image/jpeg")
                    .build()
            )

            println("MinioService: Image uploaded successfully: $fileName")
            return fileName
        } catch (e: Exception) {
            println("MinioService: Error uploading image: ${e.message}")
            throw RuntimeException("Failed to upload image: ${e.message}")
        }
    }

    /**
     * Удаляет изображение из Minio
     */
    fun deleteImage(fileName: String) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(fileName)
                    .build()
            )
            println("MinioService: Image deleted successfully: $fileName")
        } catch (e: Exception) {
            println("MinioService: Error deleting image: ${e.message}")
        }
    }

    /**
     * Генерирует URL для доступа к изображению
     */
    fun getImageUrl(fileName: String): String {
        return try {
            minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .`object`(fileName)
                    .expiry(7, TimeUnit.DAYS)
                    .build()
            )
        } catch (e: Exception) {
            println("MinioService: Error generating presigned URL: ${e.message}")
            ""
        }
    }

    /**
     * Генерирует уникальное имя файла
     */
    private fun generateFileName(gasId: Long, originalFileName: String): String {
        val extension = originalFileName.substringAfterLast('.', "jpg")
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "gas_${gasId}_${timestamp}_${uuid}.${extension}"
    }

    /**
     * Извлекает имя файла из URL
     */
    fun extractFileNameFromUrl(url: String): String? {
        return try {
            url.substringAfterLast("/")
        } catch (e: Exception) {
            null
        }
    }
}
