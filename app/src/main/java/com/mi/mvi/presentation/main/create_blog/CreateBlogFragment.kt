package com.mi.mvi.presentation.main.create_blog

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.mi.mvi.R
import com.mi.mvi.presentation.base.BaseFragment
import com.mi.mvi.presentation.common.AreYouSureCallBack
import com.mi.mvi.presentation.main.create_blog.state.CREATE_BLOG_VIEW_STATE_BUNDLE_KEY
import com.mi.mvi.presentation.main.create_blog.state.CreateBlogEventState
import com.mi.mvi.presentation.main.create_blog.state.CreateBlogViewState
import com.mi.mvi.presentation.main.create_blog.state.NewBlogFields
import com.mi.mvi.utils.Constants.Companion.GALLERY_REQUEST_CODE
import com.mi.mvi.utils.Constants.Companion.SUCCESS
import com.mi.mvi.utils.MessageType
import com.mi.mvi.utils.StateMessage
import com.mi.mvi.utils.UIComponentType
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import java.io.File
import kotlinx.android.synthetic.main.fragment_create_blog.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.android.viewmodel.ext.android.sharedViewModel

@ExperimentalCoroutinesApi
class CreateBlogFragment : BaseFragment(R.layout.fragment_create_blog) {

    private val viewModel: CreateBlogViewModel by sharedViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        blog_image.setOnClickListener {
            uiCommunicationListener?.let {
                if (it.isStoragePermissionGranted()) {
                    pickFromGallery()
                }
            }
        }
        update_textview.setOnClickListener {
            uiCommunicationListener?.let {
                if (it.isStoragePermissionGranted()) {
                    pickFromGallery()
                }
            }
        }
        subscribeObservers()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { inState ->
            (inState[CREATE_BLOG_VIEW_STATE_BUNDLE_KEY] as CreateBlogViewState?)?.let { viewState ->
                viewModel.setViewState(viewState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(
            CREATE_BLOG_VIEW_STATE_BUNDLE_KEY,
            viewModel.viewState.value
        )
        super.onSaveInstanceState(outState)
    }

    private fun subscribeObservers() {
        viewModel.dataState.observe(viewLifecycleOwner, Observer { dataState ->
            dataState?.let {
                dataStateChangeListener?.onDataStateChangeListener(dataState = dataState)
                dataState.stateMessage?.let { stateMessage ->
                    stateMessage.message.let { message ->
                        if (message == SUCCESS) {
                            viewModel.clearNewBlogFields()
                        }
                    }
                }
            }
        })
        viewModel.viewState.observe(viewLifecycleOwner, Observer { viewState ->
            viewState.newBlogField?.let {
                setBlogProperties(it)
            }
        })
    }

    private fun setBlogProperties(blogFields: NewBlogFields) {
        blogFields.newImageUri?.let {
            Glide.with(this)
                .load(it)
                .into(blog_image)
        } ?: setDefaultImage()

        blog_title.setText(blogFields.newBlogTitle)
        blog_body.setText(blogFields.newBlogBody)
    }

    private fun setDefaultImage() {
        Glide.with(this)
            .load(R.drawable.default_image)
            .into(blog_image)
    }

    private fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    private fun launchImageCrop(uri: Uri?) {
        context?.let {
            CropImage.activity(uri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .start(it, this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                GALLERY_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        activity?.let {
                            launchImageCrop(uri)
                        }
                    } ?: showErrorDialog("")
                }
                CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                    val result = CropImage.getActivityResult(data)
                    val resultUri = result.uri
                    viewModel.setNewBlogFields(
                        title = null,
                        body = null,
                        uri = resultUri
                    )
                }
                CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE -> {
                    showErrorDialog("")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.setNewBlogFields(
            blog_title.text.toString(),
            blog_body.text.toString(),
            null
        )
    }

    fun publishNewBlogPost() {
        var multipartBody: MultipartBody.Part? = null
        viewModel.getNewImageUri()?.let { imageUri ->
            imageUri.path?.let { filePath ->
                val imageFile = File(filePath)
                val requestBody = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                multipartBody =
                    MultipartBody.Part.createFormData("image", imageFile.name, requestBody)
            }
        }

        multipartBody?.let { image ->
            viewModel.setEventState(
                CreateBlogEventState.CreateNewBlogEvent(
                    blog_title.text.toString(),
                    blog_body.text.toString(),
                    image
                )
            )

            uiCommunicationListener?.hideSoftKeyboard()
        } ?: showErrorDialog("")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.publish_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.publish -> {
                val callback: AreYouSureCallBack = object :
                    AreYouSureCallBack {
                    override fun proceed() {
                        publishNewBlogPost()
                    }

                    override fun cancel() {
                    }
                }
                uiCommunicationListener?.onUIMessageReceived(
                    StateMessage(
                        getString(R.string.are_you_sure_publish),
                        UIComponentType.AreYouSureDialog(
                            callback
                        ),
                        MessageType.INFO
                    )
                )
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
