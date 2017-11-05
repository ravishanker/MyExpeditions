package com.raywenderlich.android.myexpeditions

import android.os.Bundle
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.view.*
import android.widget.Toast
import com.raywenderlich.android.myexpeditions.rendering.BasicRenderer
import org.rajawali3d.view.ISurface
import org.rajawali3d.view.SurfaceView


class MyExpeditionsActivity : AppCompatActivity() {
    private val TAG = MyExpeditionsActivity::class.java.simpleName

    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: BasicRenderer
    private lateinit var gestureDetector: GestureDetector


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_expeditions)

        setupRenderer()
        setupGestureDetector()
    }

    override fun onResume() {
        super.onResume()

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            // Note that order matters - see the note in onPause(), the reverse applies here.
            renderer.onResume()
            surfaceView.onResume()

        } else {
            CameraPermissionHelper.requestCameraPermission(this)
        }
    }

    public override fun onPause() {
        super.onPause()
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        surfaceView.onPause()
        renderer.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this,
                    "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            // Standard Android full-screen functionality.
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setupRenderer() {
        surfaceView = SurfaceView(this)
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.renderMode = ISurface.RENDERMODE_CONTINUOUSLY
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setTransparent(true)

        addContentView(surfaceView, ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT))

        renderer = BasicRenderer(this)
        surfaceView.setSurfaceRenderer(renderer)
    }

    private fun setupGestureDetector() {
        // Set up tap listener.
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                renderer.onTouchEvent(e)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })

        surfaceView.setOnTouchListener({ _, event -> gestureDetector.onTouchEvent(event) })
    }

}
