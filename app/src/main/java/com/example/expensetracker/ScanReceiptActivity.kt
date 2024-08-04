package com.example.expensetracker

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.*
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.database.FirebaseDatabase
import com.example.expensetracker.data.Transaction

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

        userID = intent.getStringExtra("userID").toString()
        Log.d("DEBUGTEST", "UserID: $userID")
        btnCreate = findViewById(R.id.btn_create)
        imgResult = findViewById(R.id.imageView2)
        // textView = findViewById(R.id.tv_result)
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

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        captureImageResultLauncher.launch(cameraIntent)
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageResultLauncher.launch(galleryIntent)
    }

    private val captureImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode == Activity.RESULT_OK && resultData.data != null) {
                val imageBitmap = resultData.data?.extras?.get("data") as Bitmap
                imgResult.setImageBitmap(imageBitmap)
                processImage(imageBitmap)
            }
        }

    private val selectImageResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultData ->
            if (resultData.resultCode == Activity.RESULT_OK && resultData.data != null) {
                val imageUri = resultData.data?.data
                imageUri?.let {
                    imgResult.setImageURI(it)
                    processImageUri(it)
                }
            }
        }

    private fun processImage(imageBitmap: Bitmap) {
        val image = InputImage.fromBitmap(imageBitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                parseExtractedText(visionText.text)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Text recognition failed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processImageUri(imageUri: Uri) {
        val image = InputImage.fromFilePath(this, imageUri)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                parseExtractedText(visionText.text)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(this, "Text recognition failed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseExtractedText(resultText: String) {
        val merchantNamePattern = Regex("Merchant: (.+)")
        val amountPattern = Regex("Total: (\\d+\\.\\d{2})")
        val datePattern = Regex("\\d{2}/\\d{2}/\\d{4}")

        val merchantName = merchantNamePattern.find(resultText)?.groupValues?.get(1)
        val amount = amountPattern.find(resultText)?.groupValues?.get(1)
        val date = datePattern.find(resultText)?.value

        editTitle.setText(merchantName ?: "")
        editAmount.setText(amount ?: "")
        editDate.setText(date ?: "")

        /*  textView.text = """
              Merchant Name: ${merchantName ?: "Not found"}
              Total Amount: ${amount ?: "Not found"}
              Date: ${date ?: "Not found"}
          """.trimIndent()*/

        Toast.makeText(this, "Information extraction completed.", Toast.LENGTH_SHORT).show()
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