package com.raywenderlich.android.myexpeditions

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.raywenderlich.android.myexpeditions.rendering.BackgroundRenderer
import kotlinx.android.synthetic.main.activity_main.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    private val TAG = MainActivity::class.java.simpleName
    private val backgroundRenderer = BackgroundRenderer()
    private var loadingMessageSnackbar: Snackbar? = null

    private lateinit var defaultConfig: Config
    private lateinit var session: Session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startArcoreSession()
        setupRenderer()

    }

    override fun onResume() {
        super.onResume()

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            showLoadingMessage()
            // Note that order matters - see the note in onPause(), the reverse applies here.
            session.resume(defaultConfig)
            surfaceview.onResume()
        } else {
            CameraPermissionHelper.requestCameraPermission(this)
        }
    }

    public override fun onPause() {
        super.onPause()
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        surfaceview?.onPause()
        session.pause()
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            val frame = session.update()

            // Draw background.
            backgroundRenderer.draw(frame)

            } catch (t: Throwable) {

            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t)
        }

    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        session.setDisplayGeometry(width.toFloat(), height.toFloat())
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f) //red, green, blue, alpha
    }

    private fun startArcoreSession() {
        session = Session(this)

        // Create default config, check is supported, create session from that config.
        defaultConfig = Config.createDefaultConfig()

        if (!session.isSupported(defaultConfig)) {
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Toast.makeText(this, "Yay! This device does support AR", Toast.LENGTH_LONG).show()
    }

    private fun setupRenderer() {
        // Rendering. The Renderers are created here, and initialized when the GL surface is created.
        surfaceview.preserveEGLContextOnPause = true
        surfaceview.setEGLContextClientVersion(2)
        surfaceview.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        surfaceview.setRenderer(this)
        surfaceview.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun showLoadingMessage() {
        runOnUiThread {
            loadingMessageSnackbar = Snackbar.make(
                    this@MainActivity.findViewById(android.R.id.content),
                    "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE)
            with(loadingMessageSnackbar) {
                this?.view?.setBackgroundColor(-0x40cdcdce)
                this?.show()
            }
        }
    }

    private fun hideLoadingMessage() {
        runOnUiThread {
            loadingMessageSnackbar?.dismiss()
            loadingMessageSnackbar = null
        }
    }


}
