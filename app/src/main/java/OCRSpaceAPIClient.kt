import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException


class OCRSpaceAPIClient {
    private val client = OkHttpClient()

    // Replace with your OCR Space API key
    private val apiKey = "K88071882588957"

    fun uploadImageForOCR(imageFile: File, callback: (String?) -> Unit) {
        val url = "https://api.ocr.space/parse/image"

        // Create a MultipartBody.Part for the image file
        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("apikey", apiKey)
            .addFormDataPart("file", imageFile.name, imageFile.asRequestBody())
            .addFormDataPart("language", "eng") // Set language for OCR
            .addFormDataPart("isTable", "true")
            .addFormDataPart("OCREngine", "2") // Add OCREngine parameter
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                println("Request failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        println("Request failed with status code: ${it.code}")
                        callback(null)
                    } else {
                        val jsonResponse = it.body?.string()
                        println("Request successful: $jsonResponse")

                        // Parse the JSON response
                        val ocrResponse = Gson().fromJson(jsonResponse, OCRSpaceResponse::class.java)
                        val parsedText = ocrResponse.ParsedResults?.firstOrNull()?.ParsedText
                        callback(parsedText)
                    }
                }
            }
        })
    }

    data class OCRSpaceResponse(
        val ParsedResults: List<ParsedResult>?
    )

    data class ParsedResult(
        val ParsedText: String?
    )
}