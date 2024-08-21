package com.example.expensetracker

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.example.expensetracker.data.Transaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
            val categoryMap = snapshot.children.associate {
                it.key!! to it.child("name").getValue(String::class.java)!!
            }

            // Add "Select Category" at the beginning of the lists
            val categoryNames = listOf("Select Category") + categoryMap.values.toList()
            val categoryIds = listOf("") + categoryMap.keys.toList()

            val adapter = ArrayAdapter(this, R.layout.spinner_category, R.id.text1, categoryNames)
            adapter.setDropDownViewResource(R.layout.spinner_category)
            spinnerCategory.adapter = adapter

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
                val bitmap = BitmapFactory.decodeFile(currentPhotoFile?.absolutePath)
                processImage(bitmap)
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
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            captureImageResultLauncher.launch(cameraIntent)
        } ?: Log.e("ScanReceipt", "Failed to create image file")
    }

    private fun createImageFile(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timestamp}_"
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: IOException) {
            Log.e("ScanReceipt", "Error creating image file", e)
            null
        }
    }


    private fun getBitmapFromUri(uri: Uri): Bitmap {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        return bitmap
    }


    private val selectImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode == Activity.RESULT_OK && resultData.data != null) {
                val imageUri = resultData.data?.data
                imageUri?.let {
                    val bitmap = getBitmapFromUri(it)
                    imgResult.setImageBitmap(bitmap)
                    processImage(bitmap)
                }
            }
        }


    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageResultLauncher.launch(galleryIntent)
    }






    private fun processImage(imageBitmap: Bitmap) {
        val image = InputImage.fromBitmap(imageBitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val resultText = visionText.text
                Log.d("DEBUGTEST", "Extracted text: $resultText")

                var merchantName: String? = null
                var date: String? = null
                var totalAmount: String? = null

                val lines = resultText.split("\n")
                for (line in lines) {
                    // Extract date
                    if (date == null) {
                        val dateRegex = Regex("\\d{1,2}/\\d{1,2}/\\d{4}")
                        val matchResult = dateRegex.find(line)
                        if (matchResult != null) {
                            date = matchResult.value
                            editDate.setText(date)
                        }
                    }

                    // Extract total amount
                    if (totalAmount == null && line.contains("Total", ignoreCase = true)) {
                        val amountRegex = Regex("\\d+(\\.\\d{2})?")
                        val matchResult = amountRegex.findAll(line)
                        matchResult.lastOrNull()?.let {
                            totalAmount = it.value
                            editAmount.setText(totalAmount)
                        }
                    }

                    // Extract merchant name (heuristic approach)
                    if (merchantName == null) {
                        val merchantNameRegex = Regex("[A-Za-z\\s]{5,}") // Assuming merchant names have 5 or more letters
                        val matchResult = merchantNameRegex.find(line)
                        if (matchResult != null && !line.contains("Invoice", ignoreCase = true)) {
                            merchantName = matchResult.value.trim()
                            editTitle.setText(merchantName)
                        }
                    }
                }

                // Log or handle cases where extraction failed
                if (date == null) Log.d("DEBUGTEST", "Date not found.")
                if (totalAmount == null) Log.d("DEBUGTEST", "Total amount not found.")
                if (merchantName == null) Log.d("DEBUGTEST", "Merchant name not found.")
            }
            .addOnFailureListener { e ->
                Log.e("DEBUGTEST", "Error processing image: ${e.message}", e)
            }
    }

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