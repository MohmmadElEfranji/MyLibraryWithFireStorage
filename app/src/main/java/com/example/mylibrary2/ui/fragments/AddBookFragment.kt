package com.example.mylibrary2.ui.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.mylibrary2.R
import com.example.mylibrary2.databinding.FragmentAddBookBinding
import com.example.mylibrary2.model.Book
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.shasin.notificationbanner.Banner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*


class AddBookFragment : Fragment() {
    private val bookCollectionRef = Firebase.firestore.collection("Books")
    private var mRate: Float = 1.5f
    private var curFile: Uri? = null

    // Instance of FirebaseStorage
    private val storage = Firebase.storage

    // Points to the root reference [Root]
    private var storageRef = storage.reference

    private lateinit var binding: FragmentAddBookBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentAddBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        //region Book review
        binding.rbRatingBar.stepSize = .5f

        binding.rbRatingBar.setOnRatingBarChangeListener { _, rating, _ ->
            mRate = rating
        }

        //endregion


        // region Change actionBar color
        val colorDrawable = ColorDrawable(Color.parseColor("#FFB300"))
        (activity as AppCompatActivity?)!!.supportActionBar!!.setBackgroundDrawable(colorDrawable)
        //endregion


        val resultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent: Intent? = result.data
                    val uri = intent?.data
                    curFile = uri
                    binding.edBookCover.setText(getString(R.string.imageSelected))
                }
            }


        binding.edBookCover.setOnClickListener {
            val intent = Intent()
                .setType("image/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            resultLauncher.launch(Intent.createChooser(intent, "Select a file"))
        }


        //region Add Button
        binding.btnAddBook.setOnClickListener {




            val idBook = bookCollectionRef.document().id
            val bookName = binding.edBookName.text.toString()
            val bookAuthor = binding.edBookAuthor.text.toString()
            val launchYear = binding.edLaunchYear.text
            val price = binding.edPrice.text.toString()
            val rate = mRate

            if (bookName.isNotEmpty() && bookAuthor.isNotEmpty() && launchYear.isNotEmpty() && price.isNotEmpty()) {
                val dateString = launchYear.toString()
                val formatter = SimpleDateFormat("yyyy", Locale.UK)
                val mLaunchYear = formatter.parse(dateString) as Date

                val file = getFile(requireContext(), curFile!!)
                val imagesRef = storageRef.child("images")
                val fileName = file.name + Calendar.getInstance().time
                val spaceRef = imagesRef.child(fileName)
                val stream = FileInputStream(file)
                val book = Book(
                    idBook,
                    fileName,
                    bookName,
                    bookAuthor,
                    mLaunchYear,
                    price.toDouble(),
                    rate
                )
                binding.progressBar.visibility = View.VISIBLE

                MaterialAlertDialogBuilder(
                    requireActivity(),
                    R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog

                )
                    .setTitle("Add Book")
                    .setMessage("Do you want to Add this book?")
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Yes") { _, _ ->
                        addBook(book)
                        val uploadTask = spaceRef.putStream(stream)
                        uploadTask.addOnFailureListener { e ->

                            Log.d("sss", "Fail ! ${e.message}")
                        }.addOnSuccessListener { taskSnapshot ->

                            Log.d("sss", "Done ! ${taskSnapshot.metadata?.sizeBytes}")
                        }

                    }
                    .show()


            } else {
                Banner.make(
                    binding.root, requireActivity(), Banner.WARNING,
                    "Fill in all fields !!", Banner.TOP, 3000
                ).show()
            }


        }

        //endregion


    }

    private fun addBook(book: Book) {
        bookCollectionRef.document(book.bookId).set(book).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Banner.make(
                    binding.root, requireActivity(), Banner.SUCCESS,
                    "Addition succeeded :)", Banner.TOP, 3000
                ).show()

                val navOptions =
                    NavOptions.Builder().setPopUpTo(R.id.homeFragment, true).build()
                findNavController().navigate(
                    R.id.action_addBookFragment_to_homeFragment,
                    null,
                    navOptions
                )

                binding.progressBar.visibility = View.GONE

            } else {
                Banner.make(
                    binding.root, requireActivity(), Banner.ERROR,
                    "Addition failed :(", Banner.TOP, 3000
                ).show()
            }

        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failure", Toast.LENGTH_SHORT).show()
        }
    }


    private fun getFile(context: Context, uri: Uri): File {
        val destinationFilename =
            File(context.filesDir.path + File.separatorChar + queryName(context, uri))
        try {
            context.contentResolver.openInputStream(uri).use { ins ->
                createFileFromStream(
                    ins!!,
                    destinationFilename
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return destinationFilename
    }


    private fun createFileFromStream(ins: InputStream, destination: File?) {
        try {
            FileOutputStream(destination).use { os ->
                val buffer = ByteArray(4096)
                var length: Int
                while (ins.read(buffer).also { length = it } > 0) {
                    os.write(buffer, 0, length)
                }
                os.flush()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun queryName(context: Context, uri: Uri): String {
        val returnCursor: Cursor = context.contentResolver.query(uri, null, null, null, null)!!
        val nameIndex: Int = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name: String = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        val callback: OnBackPressedCallback = object : OnBackPressedCallback(
            true
        ) {
            override fun handleOnBackPressed() {
                val navOptions =
                    NavOptions.Builder().setPopUpTo(R.id.homeFragment, true).build()
                findNavController().navigate(
                    R.id.action_addBookFragment_to_homeFragment,
                    null,
                    navOptions
                )
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

}