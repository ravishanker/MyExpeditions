package com.raywenderlich.android.myexpeditions.rendering

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import com.google.ar.core.*
import com.raywenderlich.android.myexpeditions.R
import org.rajawali3d.Object3D
import org.rajawali3d.animation.Animation
import org.rajawali3d.animation.Animation3D
import org.rajawali3d.animation.EllipticalOrbitAnimation3D
import org.rajawali3d.animation.RotateOnAxisAnimation
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.lights.PointLight
import org.rajawali3d.loader.LoaderOBJ
import org.rajawali3d.loader.ParsingException
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod
import org.rajawali3d.materials.textures.ATexture
import org.rajawali3d.materials.textures.Texture
import org.rajawali3d.math.Matrix4
import org.rajawali3d.math.vector.Vector3
import org.rajawali3d.primitives.Sphere
import org.rajawali3d.renderer.Renderer
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


/**
* Rajawali Basic Renderer
*/

class BasicRenderer(context: Context) : Renderer(context) {
    private val TAG = BasicRenderer::class.java.simpleName
    lateinit var earthSphere: Sphere

    private val activity: Activity = context as Activity
    private var loadingMessageSnackbar: Snackbar? = null

    private lateinit var defaultConfig: Config
    private lateinit var session: Session

    private val backgroundRenderer = BackgroundRenderer()
    private val pointCloud = PointCloudRenderer()
    private val planeRenderer = PlaneRenderer()
    private val virtualObject = ObjectRenderer()
    private val virtualObjectShadow = ObjectRenderer()

    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(16) //Tap handling and UI.
    private val touches = ArrayList<PlaneAttachment>()
    private val anchorMatrix = FloatArray(16) //Temporary matrix allocated here to reduce number of allocations for each frame.

    private lateinit var light: PointLight
    private lateinit var material: Material
    private lateinit var objGroup: Object3D
    private lateinit var cameraAnim: Animation3D
    private lateinit var lightAnim: Animation3D

    init {
        setFrameRate(60)
        startArcoreSession()
    }

    override fun initScene() {
//        addLight()
//        val material = addTexture()
//        loadEarthModel(material)

        prepareColiseumModel()
    }

    override fun onResume() {
        super.onResume()

        showLoadingMessage()
        // Note that order matters - see the note in onPause(), the reverse applies here.
        session.resume(defaultConfig)
    }

    override fun onPause() {
        super.onPause()

        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        session.pause()
    }

    override fun onRenderSurfaceCreated(config: EGLConfig?, gl: GL10?, width: Int, height: Int) {
//        super.onRenderSurfaceCreated(config, gl, width, height)

        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f) //red, green, blue, alpha

        // Create the texture and pass it to ARCore session to be filled during update().
        backgroundRenderer.createOnGlThread(activity)
        session.setCameraTextureName(backgroundRenderer.textureId)

        // Prepare the other rendering objects.
//        prepareModelDroid()
//        prepareModelColiseum()

        prepareColiseumModel()


        try {
            planeRenderer.createOnGlThread(activity, "trigrid.png")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read plane texture")
        }

        pointCloud.createOnGlThread(activity)
    }



    override fun onRenderSurfaceSizeChanged(gl: GL10?, width: Int, height: Int) {
//        super.onRenderSurfaceSizeChanged(gl, width, height)

        GLES20.glViewport(0, 0, width, height)
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        session.setDisplayGeometry(width.toFloat(), height.toFloat())
    }

    override fun onRenderFrame(gl: GL10?) {
        super.onRenderFrame(gl)

        detectPlane()
    }

    override fun onTouchEvent(event: MotionEvent?) {
        // Queue tap if there is space. Tap is lost if queue is full.
        queuedSingleTaps.offer(event)
    }

    override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
        //To change body of created functions use File | Settings | File Templates.
    }

//    override fun onRender(ellapsedRealtime: Long, deltaTime: Double) {
//        super.onRender(ellapsedRealtime, deltaTime)
//        earthSphere.rotate(Vector3.Axis.Y, 1.0)
//    }

    private fun startArcoreSession() {
        session = Session(activity)

        // Create default config, check is supported, create session from that config.
        defaultConfig = Config.createDefaultConfig()

        if (!session.isSupported(defaultConfig)) {
            Toast.makeText(context, "This device does not support AR", Toast.LENGTH_LONG).show()

            return
        }

        Toast.makeText(context, "Yay! This device does support AR", Toast.LENGTH_LONG).show()
    }

    private fun detectPlane() {
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
//                        drawDroid(scaleFactor, viewmtx, projmtx, lightIntensity)

                        //drawColiseum(.002f, viewmtx, projmtx, lightIntensity)

                        drawColiseumModel(viewmtx, projmtx, lightIntensity)
                    }

        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t)
        }
    }

    private fun prepareColiseumModel() {
        light = PointLight()
        light.setPosition(0.0, 0.0, 4.0)
        light.power = 3f

        currentScene.addLight(light)
        currentCamera.z = 16.0

        val coliseumTexture = Texture("coliseumTexture", R.drawable.coliseum_texture)
        val coliseumGroundTexture = Texture("coliseumGroundTexture", R.drawable.ground_texture)

        try {
            material = Material()
            material.enableLighting(true)
            material.diffuseMethod = DiffuseMethod.Lambert()
            material.colorInfluence = 0f

        } catch (error: ATexture.TextureException) {
            Log.d("initScene", error.toString())
        }
//        material.addTexture(coliseumTexture)
//        material.addTexture(coliseumGroundTexture)

        val objParser = LoaderOBJ(this, R.raw.coliseum_obj)

        try {
            objParser.parse()
            objGroup = objParser.parsedObject
            objGroup.material = material
            currentScene.addChild(objGroup)

        } catch (e: ParsingException) {
            e.printStackTrace()
        }


        cameraAnim = RotateOnAxisAnimation(Vector3.Axis.Y, 360.0)
        cameraAnim.durationMilliseconds = 8000
        cameraAnim.repeatMode = Animation.RepeatMode.INFINITE
        cameraAnim.transformable3D = objGroup


        lightAnim = EllipticalOrbitAnimation3D(
                Vector3(),
                Vector3(0.0, 10.0, 0.0),
                Vector3.getAxisVector(Vector3.Axis.Z),
                0.0,
                360.0,
                EllipticalOrbitAnimation3D.OrbitDirection.CLOCKWISE)

        lightAnim.durationMilliseconds = 3000
        lightAnim.repeatMode = Animation.RepeatMode.INFINITE
        lightAnim.transformable3D = light
    }

    private fun drawColiseumModel(cameraView: FloatArray, cameraPerspective: FloatArray, lightIntensity: Float) {

//        material.setModelViewMatrix(Matrix4(cameraView))
//        material.setMVPMatrix(Matrix4(cameraPerspective))
//        material.setAmbientIntensity(lightIntensity, lightIntensity, lightIntensity)

//        objGroup.modelViewMatrix.add(Matrix4(cameraView))
//        objGroup.modelViewProjectionMatrix.add(Matrix4(cameraPerspective))

        currentScene.addChild(objGroup)
        currentScene.registerAnimation(cameraAnim)
        currentScene.registerAnimation(lightAnim)


        cameraAnim.play()
        lightAnim.play()
    }


    private fun prepareModelColiseum() {
        try {
            virtualObject.createOnGlThread(/*context=*/activity, "Coliseum.obj", "Coliseum Texture.png")
            virtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read obj file")
        }
    }

    private fun drawColiseum(scaleFactor: Float, viewmtx: FloatArray, projmtx: FloatArray, lightIntensity: Float) {
        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
        virtualObject.draw(viewmtx, projmtx, lightIntensity)
    }



    private fun prepareModelDroid() {
        try {
            virtualObject.createOnGlThread(/*context=*/activity, "andy.obj", "andy.png")
            virtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)

            virtualObjectShadow.createOnGlThread(/*context=*/activity,
                    "andy_shadow.obj", "andy_shadow.png")
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow)
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read obj file")
        }
    }

    private fun drawDroid(scaleFactor: Float, viewmtx: FloatArray, projmtx: FloatArray, lightIntensity: Float) {
        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
        virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor)

        virtualObject.draw(viewmtx, projmtx, lightIntensity)
        virtualObjectShadow.draw(viewmtx, projmtx, lightIntensity)
    }

    private fun handleTaps(frame: Frame) {
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

    private fun addLight() {
        val directionalLight = DirectionalLight(1.0, 0.2, -1.0)
        directionalLight.setColor(1.0f, 1.0f, 1.0f)
        directionalLight.power = 2f
        currentScene.addLight(directionalLight)
    }

    private fun addTexture(): Material {
        val material = Material()
        material.enableLighting(true)
        material.diffuseMethod = DiffuseMethod.Lambert()
        material.colorInfluence = 0f

        val earthTexture = Texture("Earth", R.drawable.earthtruecolor_nasa_big)

        try {
            material.addTexture(earthTexture)
        } catch (error: ATexture.TextureException) {
            Log.d("initScene", error.toString())
        }
        return material
    }

    private fun loadEarthModel(material: Material) {
        earthSphere = Sphere(1f, 24, 24)
        earthSphere.material = material
        currentScene.addChild(earthSphere)
        currentCamera.z = 4.2
    }

    private fun showLoadingMessage() {
        activity.runOnUiThread {
            loadingMessageSnackbar = Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE)
            with(loadingMessageSnackbar) {
                this?.view?.setBackgroundColor(-0x40cdcdce)
                this?.show()
            }
        }
    }

    private fun hideLoadingMessage() {
        activity.runOnUiThread {
            loadingMessageSnackbar?.dismiss()
            loadingMessageSnackbar = null
        }
    }

}

