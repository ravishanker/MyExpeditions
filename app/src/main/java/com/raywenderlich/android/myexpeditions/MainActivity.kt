/*
 * Copyright  2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modifications Copyright (c) 2017 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.myexpeditions

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import com.google.ar.core.*
import com.google.atap.tangoservice.*
import com.raywenderlich.android.myexpeditions.rendering.*
import kotlinx.android.synthetic.main.activity_main.*
import org.rajawali3d.Object3D
import org.rajawali3d.animation.Animation
import org.rajawali3d.animation.RotateOnAxisAnimation
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod
import org.rajawali3d.materials.textures.ATexture
import org.rajawali3d.materials.textures.StreamingTexture
import org.rajawali3d.materials.textures.Texture
import org.rajawali3d.math.Matrix4
import org.rajawali3d.math.Quaternion
import org.rajawali3d.math.vector.Vector3
import org.rajawali3d.primitives.Cube
import org.rajawali3d.primitives.ScreenQuad
import org.rajawali3d.primitives.Sphere
import org.rajawali3d.renderer.Renderer
import org.rajawali3d.scene.ASceneFrameCallback
import org.rajawali3d.util.ObjectColorPicker
import org.rajawali3d.util.OnObjectPickedListener
import org.rajawali3d.view.ISurface
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName
    private var loadingMessageSnackbar: Snackbar? = null

    private val backgroundRenderer = BackgroundRenderer()
    private val pointCloud = PointCloudRenderer()
    private val planeRenderer = PlaneRenderer()
    private val virtualObject = ObjectRenderer()
    private val virtualObjectShadow = ObjectRenderer()
    private lateinit var modelRenderer: ModelRenderer

    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(16) //Tap handling and UI.
    private val touches = ArrayList<PlaneAttachment>()
    private val anchorMatrix = FloatArray(16) //Temporary matrix allocated here to reduce number of allocations for each frame.

    private lateinit var defaultConfig: Config
    private lateinit var session: Session
    private lateinit var gestureDetector: GestureDetector


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startArcoreSession()
        setupRenderer()
        setupGestureDetector()
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
        surfaceview.onPause()
        session.pause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
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
        modelRenderer = ModelRenderer(this)

        surfaceview.preserveEGLContextOnPause = true
        surfaceview.setTransparent(true)
        surfaceview.setEGLContextClientVersion(2)
        surfaceview.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        surfaceview.setSurfaceRenderer(modelRenderer)
        surfaceview.renderMode = ISurface.RENDERMODE_CONTINUOUSLY
    }

    private fun setupGestureDetector() {
        // Set up tap listener.
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
//                onSingleTap(e)
                modelRenderer.onTouchEvent(e)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })

        surfaceview.setOnTouchListener({ _, event -> gestureDetector.onTouchEvent(event) })
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


    /**
     * Renderer that implements a basic augmented reality scene using Rajawali.
     * It creates a scene with a background quad taking the whole screen, where the color camera is
     * rendered and a sphere with the texture of the earth floats ahead of the start position of
     * the Tango device.
     */
    inner class ModelRenderer(context: Context) : Renderer(context, true), OnObjectPickedListener {

        private lateinit var light: DirectionalLight
        private lateinit var monkey: Cube
        private lateinit var material:Material
        private lateinit var anim: RotateOnAxisAnimation

        private lateinit var onePicker: ObjectColorPicker
        private lateinit var backgroundQuad: ScreenQuad
        private val textureCoords0 = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f)

        // Keeps track of whether the scene camera has been configured.
        private var sceneCameraConfigured: Boolean = false

        // Rajawali texture used to render the Tango color camera.
        private lateinit var cameraTexture: ATexture


        override fun initScene() {
//            preparePyramidModel()
            onePicker = ObjectColorPicker(this)

            // Create a quad covering the whole background and assign a texture to it where
            // the color camera contents will be rendered.
            val cameraMaterial = Material()
            cameraMaterial.colorInfluence = 0F

            backgroundQuad = ScreenQuad()
            backgroundQuad.geometry.setTextureCoords(textureCoords0)

            // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
            // for GL_TEXTURE_EXTERNAL_OES rendering.
            cameraTexture = StreamingTexture("camera", null as StreamingTexture.ISurfaceListener?)

            try {
                cameraMaterial.addTexture(cameraTexture)
                backgroundQuad.material = cameraMaterial
            } catch (e: ATexture.TextureException) {
                Log.e(TAG, "Exception creating texture for RGB camera contents", e)
            }

            currentScene.addChildAt(backgroundQuad, 0)

            // Add a directional light in an arbitrary direction.
            val light = DirectionalLight(1.0, 0.2, -1.0)
            light.setColor(1f, 1f, 1f)
            light.power = 0.8f
            light.setPosition(3.0, 2.0, 4.0)
            currentScene.addLight(light)

            // Create sphere with earth texture and place it in space 3m forward from the origin.
            val earthMaterial = Material()

            try {
                val t = Texture("earth", R.drawable.earth)
                earthMaterial.addTexture(t)
            } catch (e: ATexture.TextureException) {
                Log.e(TAG, "Exception generating earth texture", e)
            }

            earthMaterial.colorInfluence = 0F
            earthMaterial.enableLighting(true)
            earthMaterial.diffuseMethod = DiffuseMethod.Lambert()

            val earth = Sphere(0.4f, 20, 20)
            earth.material = earthMaterial
            earth.setPosition(0.0, 0.0, -3.0)
            currentScene.addChild(earth)

            // Rotate around its Y axis
            val animEarth = RotateOnAxisAnimation(Vector3.Axis.Y, 0.0, -360.0)
            animEarth.interpolator = LinearInterpolator()
            animEarth.durationMilliseconds = 60000;
            animEarth.repeatMode = Animation.RepeatMode.INFINITE;
            animEarth.transformable3D = earth;
            currentScene.registerAnimation(animEarth)
            animEarth.play()

            onePicker.registerObject(earth)
            onePicker.registerObject(backgroundQuad)
        }


        /**
         * We need to override this method to mark the camera for re-configuration (set proper
         * projection matrix) since it will be reset by Rajawali on surface changes.
         */
        override fun onRenderSurfaceCreated(config: EGLConfig?, gl: GL10?, width: Int, height: Int) {
//            super.onRenderSurfaceCreated(config, gl, width, height)
            sceneCameraConfigured = false

            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f) //red, green, blue, alpha

            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(context)
            session.setCameraTextureName(backgroundRenderer.textureId)

            // Prepare the other rendering objects.
//            prepareDroidModel()

//            preparePyramidModel()

            try {
                planeRenderer.createOnGlThread(context, "trigrid.png")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read plane texture")
            }

            pointCloud.createOnGlThread(context)
        }

        override fun onRenderSurfaceSizeChanged(gl: GL10?, width: Int, height: Int) {
//            super.onRenderSurfaceSizeChanged(gl, width, height)

            GLES20.glViewport(0, 0, width, height)
            // Notify ARCore session that the view size changed so that the perspective matrix and
            // the video background can be properly adjusted.
            session.setDisplayGeometry(width.toFloat(), height.toFloat())

            sceneCameraConfigured = false
        }

        override fun onRenderFrame(gl: GL10?) {
            super.onRenderFrame(gl)

            // Clear screen to notify driver it should not load any pixels from previous frame.
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            try {
                // Obtain the current frame from ARSession. When the configuration is set to
                // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
                // camera framerate.
                val frame = session.update()

                handleTaps(frame)

                // Draw background.
                backgroundRenderer.draw(frame)

                // If not tracking, don't draw 3d objects.
                if (frame.trackingState == Frame.TrackingState.NOT_TRACKING) {
                    return
                }

                // Get projection matrix.
                val projmtx = FloatArray(16)
                session.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f) //10cm to 100m

                // Get camera matrix and draw.
                val viewmtx = FloatArray(16)
                frame.getViewMatrix(viewmtx, 0)

                // Compute lighting from average intensity of the image.
                val lightIntensity = frame.lightEstimate.pixelIntensity

                // Visualize tracked points.
                pointCloud.update(frame.pointCloud)
                pointCloud.draw(frame.pointCloudPose, viewmtx, projmtx)

                // Check if we detected at least one plane. If so, hide the loading message.
                if (loadingMessageSnackbar != null) {
                    for (plane in session.allPlanes) {
                        if (plane.type == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
                                && plane.trackingState == Plane.TrackingState.TRACKING) {
                            hideLoadingMessage()
                            break
                        }
                    }
                }

                // Visualize planes.
                planeRenderer.drawPlanes(session.allPlanes, frame.pose, projmtx)

                // Visualize anchors created by touch.
                val scaleFactor = 1.0f

                // Get the current combined pose of an Anchor and Plane in world space. The Anchor
                // and Plane poses are updated during calls to session.update() as ARCore refines
                // its estimate of the world.
                touches.filter { it.isTracking }
                        .forEach {
                            it.pose.toMatrix(anchorMatrix, 0)

                            // Update and draw the model and its shadow.
//                            renderDroidModel(scaleFactor, viewmtx, projmtx, lightIntensity)

                            preparePyramidModel()


                        }

            } catch (t: Throwable) {
                // Avoid crashing the application due to unhandled exceptions.
                Log.e(TAG, "Exception on the OpenGL thread", t)
            }
        }


        override fun onTouchEvent(event: MotionEvent?) {
            // Queue tap if there is space. Tap is lost if queue is full.
            queuedSingleTaps.offer(event)

            if (event?.action == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "Pick object attempt");
                event.x.let { onePicker.getObjectAt(it, event.y) }
            }
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            //To change body of created functions use File | Settings | File Templates.
        }

        override fun onNoObjectPicked() {
            Log.d(TAG, "Picked no object")
        }

        override fun onObjectPicked(`object`: Object3D) {
            Log.d(TAG, "Picked object: " + `object`)
        }

        fun handleTaps(frame: Frame) {
            // Handle taps. Handling only one tap per frame, as taps are usually low frequency
            // compared to frame rate.
            val tap = queuedSingleTaps.poll()
            if (tap != null && frame.trackingState == Frame.TrackingState.TRACKING) {
                for (hit in frame.hitTest(tap)) {
                    // Check if any plane was hit, and if it was hit inside the plane polygon.
                    if (hit is PlaneHitResult && hit.isHitInPolygon) {
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (touches.size >= 16) {
                            session.removeAnchors(Arrays.asList(touches.get(0).anchor))
                            touches.removeAt(0)
                        }
                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor will be used in PlaneAttachment to place the 3d model
                        // in the correct position relative both to the world and to the plane.
                        touches.add(PlaneAttachment(
                                hit.plane,
                                session.addAnchor(hit.getHitPose())))

                        // Hits are sorted by depth. Consider only closest hit on a plane.
                        break
                    }
                }
            }
        }


        private fun preparePyramidModel() {
            try {

                light = DirectionalLight(0.0, 0.0, -1.0)
                light.power = 1F
                currentScene.addLight(light)
                currentCamera.setPosition(0.0, 0.0, 16.0)

                monkey = Cube(2.0f)

                material = Material()
                material.useVertexColors(true)
                material.enableLighting(true)
                material.diffuseMethod = DiffuseMethod.Lambert()

                monkey.material = material
                monkey.setColor(Vector3(16747520.0))
                monkey.scale = Vector3(2.0)
                currentScene.addChild(monkey)

                anim = RotateOnAxisAnimation(Vector3.Axis.Y, 360.0)
                anim.durationMilliseconds = 6000
                anim.repeatMode = Animation.RepeatMode.INFINITE
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.transformable3D = monkey
                currentScene.registerAnimation(anim)
                anim.play()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to render model")
                Log.e(TAG, e.message)
            }

            // -- set the background color to be transparent
            // you need to have called setTransparent(true); in the activity
            // for this to work.
            currentScene.backgroundColor = Color.TRANSPARENT
        }

        private fun renderPyramidModel() {
            currentScene.addLight(light)
            currentCamera.setPosition(0.0, 0.0, 16.0)

            currentScene.addChild(monkey)

            currentScene.registerAnimation(anim)
            anim.play()
        }

        private fun prepareDroidModel() {
            try {
                virtualObject.createOnGlThread(/*context=*/context, "andy.obj", "andy.png")
                virtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)

                virtualObjectShadow.createOnGlThread(/*context=*/context,
                        "andy_shadow.obj", "andy_shadow.png")
                virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow)
                virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read obj file")
            }
        }

        private fun renderDroidModel(scaleFactor: Float, viewmtx: FloatArray, projmtx: FloatArray, lightIntensity: Float) {
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
            virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor)

            virtualObject.draw(viewmtx, projmtx, lightIntensity)
            virtualObjectShadow.draw(viewmtx, projmtx, lightIntensity)
        }


    }


}
