package com.abhi.objectdector

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.abhi.objectdector.databinding.ActivityMainBinding
import com.abhi.objectdector.utils.Draw
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var objectDetector : ObjectDetector
    private lateinit var cameraProvideFuture: ListenableFuture<ProcessCameraProvider>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main)

        val modelPath = "model.tflite"
        val localModel = LocalModel.Builder()
            .setAssetFilePath(modelPath)
            .build()

        val customObjectDetectorOptions = CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .setClassificationConfidenceThreshold(0.5f)
            .setMaxPerObjectLabelCount(3)
            .build()

        cameraProvideFuture = ProcessCameraProvider.getInstance(this)

        cameraProvideFuture.addListener({
            val cameraProvider = cameraProvideFuture.get()
            bindPreview(cameraProvider = cameraProvider)
        },ContextCompat.getMainExecutor(this))

        objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider){

         val preview = Preview.Builder().build()

         val cameraSelector = CameraSelector.Builder()
             .requireLensFacing(CameraSelector.LENS_FACING_BACK)
             .build()

        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280,720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this),{ imageProxy->
            val rotationDegree = imageProxy.imageInfo.rotationDegrees
            val image = imageProxy.image

            if(image!=null){
                val processImage = InputImage.fromMediaImage(image,rotationDegree)

                objectDetector
                    .process(processImage)
                    .addOnSuccessListener { objects ->
                        if(binding.parentLayout.childCount>1) binding.parentLayout.removeViewAt(1)

                        for (i in objects){
                          val element = Draw(context = this,
                              rect = i.boundingBox,
                              text = i.labels.firstOrNull()?.text?:"Undefined")

                            binding.parentLayout.addView(element)
                        }

                        imageProxy.close()
                    }.addOnFailureListener{
                        Log.v("MainActivity","Error - ${it.message}")
                        imageProxy.close()
                    }
            }

        })
        cameraProvider.bindToLifecycle(this as LifecycleOwner,cameraSelector,imageAnalysis,preview)
    }
}