package com.example.expensetracker
import OCRSpaceAPIClient
import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.network.RetrofitClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class ScanReceiptActivity : AppCompatActivity() {



    private lateinit var imgResult: ImageView
    private lateinit var editTitle: EditText
    private lateinit var editAmount: EditText
    private lateinit var editDate: EditText
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var userID: String
    private lateinit var btnCreate: AppCompatButton
    private lateinit var spinnerCategory: Spinner
    private var selectedCategoryId: String? = null // To store the selected category ID
    private val categoriesMap = mutableMapOf<String, String>() // Maps categoryId to categoryName



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_receipt)








        userID = intent.getStringExtra("userID") ?: ""
        Log.d("DEBUGTEST", "Intent Extras: ${intent.extras}")




        Log.d("DEBUGTEST", "UserID: $userID")
        btnCreate = findViewById(R.id.btn_create)
        imgResult = findViewById(R.id.imageView2)
        editTitle = findViewById(R.id.edit_title)
        editAmount = findViewById(R.id.edit_amount)
        editDate = findViewById(R.id.edit_Date)
        spinnerCategory = findViewById(R.id.spinner_category) // Add this line
        val btnCapture: FloatingActionButton = findViewById(R.id.btn_add_scan)




        // Initialize the text recognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)




        val iconBack: ImageView = findViewById(R.id.icon_back)
        iconBack.setOnClickListener {
            finish()
        }




        btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
            } else {
                showImageSourceDialog()
            }
        }




        btnCreate.setOnClickListener {
            generateNewTransactionId { newId ->
                saveTransaction(newId)
            }
        }




        // Set OnClickListener for editDate to show DatePickerDialog
        editDate.setOnClickListener {
            showDatePickerDialog()
        }




        // Fetch categories for the spinner
        fetchCategories()
    }




    private fun generateNewTransactionId(callback: (String) -> Unit) {
        val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")




        transRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Extract and parse IDs
                val ids = snapshot.children.mapNotNull {
                    it.child("id").getValue(String::class.java)
                }




                // Extract numeric part from IDs
                val maxId = ids.mapNotNull { id ->
                    id.replace("T", "").toIntOrNull()
                }.maxOrNull() ?: 0




                // Generate new ID
                val newId = "T${maxId + 1}"




                // Invoke callback with the new ID
                callback(newId)
            } else {
                // Handle case where there are no transactions yet
                val newId = "T1"
                callback(newId)
            }
        }.addOnFailureListener { exception ->
            Log.e("ScanReceipt", "Error fetching transactions: ${exception.message}", exception)
        }
    }




    private fun saveTransaction(newId: String) {
        Log.d("DEBUGTEST", "saveTransaction method called with ID: $newId")
        val title = editTitle.text.toString()
        val amount = editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val date = editDate.text.toString()
        val notes = "" // Assuming you have an EditText for notes, add it here if needed
        val categoryId = selectedCategoryId ?: return // Ensure a category is selected
        Log.d("DEBUGTEST", "Selected Category ID: $categoryId")




        if (title.isNotEmpty() && amount != 0.0 && date.isNotEmpty()) {
            val transRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Transaction")




            val transaction = Transaction(newId, title, amount, date, categoryId, notes)
            transRef.child(newId).setValue(transaction).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Update user's transaction list
                    val userRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("User").child(userID).child("transactionList")




                    // Get current maximum index for sorting
                    userRef.get().addOnSuccessListener { snapshot ->
                        val maxIndex = snapshot.children.mapNotNull {
                            it.key?.split(":")?.firstOrNull()?.toIntOrNull()
                        }.maxOrNull() ?: 0




                        val newIndex = maxIndex + 1




                        userRef.child(newIndex.toString()).setValue(newId).addOnCompleteListener { userTask ->
                            if (userTask.isSuccessful) {
                                finish() // Close the activity after successful record
                            } else {
                                Log.e("DEBUGTEST", "Error updating user transaction list: ${userTask.exception?.message}")
                            }
                        }
                    }.addOnFailureListener { exception ->
                        Log.e("DEBUGTEST", "Error fetching transaction list for sorting: ${exception.message}", exception)
                    }
                } else {
                    Log.e("DEBUGTEST", "Error recording transaction: ${task.exception?.message}")
                }
            }
        } else {
            // Handle missing fields
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
        }
    }




    private fun fetchCategories() {
        val categoriesRef = FirebaseDatabase.getInstance("https://expensetracker-a260c-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("Category")
        categoriesRef.get().addOnSuccessListener { snapshot ->
            categoriesMap.clear()
            val categoryMap = snapshot.children.associate {
                it.key!! to it.child("name").getValue(String::class.java)!!
            }

            // Add "Select Category" at the beginning of the lists
            val categoryNames = listOf("Select Category") + categoryMap.values.toList()
            val categoryIds = listOf("") + categoryMap.keys.toList()


            val adapter = ArrayAdapter(this, R.layout.spinner_category, R.id.text1, categoryNames)
            adapter.setDropDownViewResource(R.layout.spinner_category)
            spinnerCategory.adapter = adapter

// Store categories in memory for later use
            categoriesMap.putAll(categoryMap)
            // Log the contents of categoriesMap after adding
            Log.d("CategoryMap", "Updated Categories Map: $categoriesMap")


            spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (position == 0) {
                        selectedCategoryId = null
                    } else {
                        selectedCategoryId = categoryIds[position]
                    }
                }


                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Do nothing
                }
            }
        }.addOnFailureListener { exception ->
            Log.e("DEBUGTEST", "Error fetching categories: ${exception.message}")
        }
    }




    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)




        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Format the date as you like
                val formattedDate = "${selectedDay}/${selectedMonth + 1}/${selectedYear}"
                editDate.setText(formattedDate)
            },
            year, month, day
        )
        datePickerDialog.show()
    }




    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Image Source")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }




    private val captureImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode == Activity.RESULT_OK) {
                // Use the imageUri set earlier to decode the image
                val imageUri = currentPhotoUri
                imgResult.setImageURI(imageUri)


                imageUri?.let {
                    Glide.with(this)
                        .asBitmap()
                        .load(it)
                        .override(1024, 1024) // Set desired width and height
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                val resizedFile = File.createTempFile("resized_", ".jpg", cacheDir)
                                saveBitmapToFile(resource, resizedFile)
                                processImageWithOCRSpace(resizedFile)
                            }


                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            }
        }


    private var currentPhotoFile: File? = null
    private var currentPhotoUri: Uri? = null




    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        currentPhotoFile = createImageFile()
        currentPhotoFile?.also {
            val uri = FileProvider.getUriForFile(this, "${this.packageName}.provider", it)
            currentPhotoUri = uri
            Log.d("ScanReceiptURL", "File URI: $uri") // Log the URI
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            captureImageResultLauncher.launch(cameraIntent)
        } ?: Log.e("ScanReceipt", "Failed to create image file")
    }




    private fun createImageFile(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timestamp}_"
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            // File.createTempFile(imageFileName, ".jpg", storageDir)
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            Log.d("ScanReceipt", "Created file: ${imageFile.absolutePath}")
            imageFile
        } catch (e: IOException) {
            Log.e("ScanReceipt", "Error creating image file", e)
            null
        }
    }


    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        val inputStream = contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, this)


            // Calculate inSampleSize
            val (sampleSize, _) = calculateInSampleSize(this, 1024, 1024)
            inSampleSize = sampleSize
            inJustDecodeBounds = false
        }


        inputStream?.close()
        val newInputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(newInputStream, null, options)
    }


    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Pair<Int, Int> {
        val (width, height) = options.outWidth to options.outHeight
        var inSampleSize = 1


        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2


            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize to (height / inSampleSize)
    }




    private val selectImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode == Activity.RESULT_OK && resultData.data != null) {
                val imageUri = resultData.data?.data
                imageUri?.let {
                    Glide.with(this)
                        .asBitmap()
                        .load(it)
                        .override(1024, 1024) // Set desired width and height
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                imgResult.setImageBitmap(resource)


                                // Save bitmap to a file and process with OCR Space
                                val imageFile = createImageFile()
                                imageFile?.let { file ->
                                    saveBitmapToFile(resource, file)
                                    processImageWithOCRSpace(file)
                                }
                            }


                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            }
        }




    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            var outputStream = file.outputStream()
            var quality = 100
            var fileSize: Long


            // Resize the bitmap if it is too large
            val maxWidth = 1024 // Example width
            val maxHeight = 1024 // Example height
            val scaledBitmap = getScaledBitmap(bitmap, maxWidth, maxHeight)


            do {
                outputStream.flush()
                outputStream.close()
                outputStream = file.outputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                fileSize = file.length()
                quality -= 5
            } while (fileSize >= 1024 * 1024 && quality > 0) // 1 MB limit


            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            Log.e("ScanReceipt", "Error saving bitmap to file", e)
        }
    }




    private fun getScaledBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scale = Math.min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)


        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()


        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }










    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageResultLauncher.launch(galleryIntent)
    }
    private fun processImageWithOCRSpace(imageFile: File) {
        Glide.with(this)
            .asBitmap()
            .load(imageFile)
            .override(1024, 1024) // Set desired width and height
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val resizedFile = File.createTempFile("resized_", ".jpg", cacheDir)
                    saveBitmapToFile(resource, resizedFile)
                    val ocrClient = OCRSpaceAPIClient()
                    ocrClient.uploadImageForOCR(resizedFile) { parsedText ->
                        runOnUiThread {
                            Log.d("ParsedText", "Extracted text: $parsedText")
                            if (parsedText != null) {
                                // Extract relevant information from the parsed text
                                val invoiceDetails = extractInvoiceDetails(parsedText)
                                // Update UI fields with extracted values
                                editTitle.setText(invoiceDetails.title)
                                editAmount.setText(invoiceDetails.amount)
                                editDate.setText(invoiceDetails.date)
//                                Toast.makeText(this@ScanReceiptActivity, "Extracted text: $parsedText", Toast.LENGTH_LONG).show()
                                // Send the extracted text to MeaningCloud API for classification
                                classifyTextWithMeaningCloud(parsedText)
                            } else {
                                Toast.makeText(this@ScanReceiptActivity, "Failed to extract text from image.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Log.d("OCRProcess", "OCR request has been sent, awaiting results...")
                    }
                }


                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun classifyTextWithMeaningCloud(text: String) {
        val apiKey = "ae0d5e86ab8d3f9177d5c0d14e41aa92" // Replace with your MeaningCloud API key
        val language = "en"
        val model = "IAB_2.0-tier4"

        // Use the RetrofitClient service
        val service = RetrofitClient.service

        // Make the API call within a coroutine
        lifecycleScope.launch {
            try {
                val response = service.classifyText(apiKey, text, language, model)
                if (response.isSuccessful) {
                    val classificationResponse = response.body()

                    // Log the entire response to inspect the fields
                    Log.d("MeaningCloud", "Full Response: ${classificationResponse}")

                    // Use the correct field names as per the API response structure
                    val categoryList = classificationResponse?.category_list
                    if (categoryList != null) {
                        Log.d("MeaningCloud", "Classification: $categoryList")


                        // Transforming categoryList into a list of ApiCategory
                        val apiCategories = categoryList.map { category ->
                            ApiCategory(
                                code = category.code,
                                label = category.label,
                                score = category.score
                            )
                        }

// Logging the result
                        Log.d("ApiCategories", "Transformed Categories: $apiCategories")
                        testCategoryMatching(apiCategories, categoriesMap,spinnerCategory)

                        // Update UI or database as needed
                    } else {
                        Log.e("MeaningCloud", "categoryList is null in the response")
                        Toast.makeText(this@ScanReceiptActivity, "Failed to classify text.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("MeaningCloud", "Failed to classify text: ${response.errorBody()?.string()}")
                    Toast.makeText(this@ScanReceiptActivity, "API call failed with status code ${response.code()}.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MeaningCloud", "Error: ${e.message}", e)
                Toast.makeText(this@ScanReceiptActivity, "An error occurred while classifying text.", Toast.LENGTH_SHORT).show()
            }
        }
    }



    data class ApiCategory(val code: String, val label: String, val score: Double)

    private fun testCategoryMatching(apiCategories: List<ApiCategory>, categoriesMap: Map<String, String>, spinner: Spinner) {
        // Iterate over each category from the API
        for (apiCategory in apiCategories) {
            val apiLabel = apiCategory.label

            // Flag to check if there is a match
            var matchFound = false

            // Iterate over the categories map to find a match
            for ((categoryId, categoryValue) in categoriesMap) {
                if (apiLabel.contains(categoryValue, ignoreCase = true)) {
                    println("API Category: \"$apiLabel\" matches with Map Category ID: $categoryId ($categoryValue)")
                    Log.d("CategoryMatch", "API Category: \"$apiLabel\" matches with Map Category ID: $apiLabel ($categoryValue)")
                    matchFound = true
                    // Set the spinner value to the matching category
                    val position = (spinner.adapter as ArrayAdapter<String>).getPosition(categoryValue)
                    spinner.setSelection(position)
                    break // Exit inner loop if a match is found
                }
            }

            if (!matchFound) {
                println("API Category: \"$apiLabel\" does not match any category in the map.")
                Log.d("CategoryMatch", "No match found for API Category: \"$apiLabel\"")
            }
        }
    }

    private fun extractInvoiceDetails(parsedText: String): InvoiceDetails {




        // Implement logic to extract title, amount, and date from the parsed text
        // Assuming merchant names have 5 or more letters


        val titleRegex = Regex("[A-Za-z\\s]{5,}")
        // Regex for amount with flexible matching for "total" variations
        // val amountRegex = Regex("(?i)(total|net\\s*total|total\\s*rounded)\\s*:?\\s*(\\d+(?:\\.\\d{2})?)")
        val amountRegex = Regex(
            "(?i)(?<!sub)(total|net\\s*total|total\\s*rounded|total\\s*sales\\s*inclusive\\s*of\\s*sst|takeout\\s*total\\s*\\(incl\\s*tax\\))\\s*:?\\s*(\\d+(?:\\.\\d{2})?)"
        )




        val dateRegexes = listOf(
            Regex("\\d{1,2}/\\d{1,2}/\\d{4}"), // 12/02/2024
            Regex("\\d{1,2} [A-Za-z]{3} \\d{4}"), // 12 Jul 2024
            Regex("\\d{1,2}-\\d{1,2}-\\d{4}") // 12-02-2024
        )


        val titleMatch = titleRegex.find(parsedText)
        val amountMatch = amountRegex.find(parsedText)
        var dateMatch: MatchResult? = null
        for (dateRegex in dateRegexes) {


            dateMatch = dateRegex.find(parsedText)
            if (dateMatch != null) break
        }
        val title = titleMatch?.value ?: ""


        // Prepend "-" to the extracted amount
        val amount = "-" + (amountMatch?.groupValues?.get(2) ?: "")
        val date = dateMatch?.value ?: ""


        return InvoiceDetails(title, amount, date)
    }


    data class InvoiceDetails(val title: String, val amount: String, val date: String)










    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                showImageSourceDialog()
            } else {
                Toast.makeText(this, "Camera permission is required to capture images", Toast.LENGTH_SHORT).show()
            }
        }
    }




    companion object {
        private const val CAMERA_REQUEST_CODE = 101
    }
}