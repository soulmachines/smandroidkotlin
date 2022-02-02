package com.soulmachines.android.sample

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.preference.PreferenceManager
import com.soulmachines.android.sample.databinding.ActivityMainBinding
import com.soulmachines.android.smsdk.core.SessionInfo
import com.soulmachines.android.smsdk.core.UserMedia
import com.soulmachines.android.smsdk.core.async.Completion
import com.soulmachines.android.smsdk.core.async.CompletionError
import com.soulmachines.android.smsdk.core.scene.*
import com.soulmachines.android.smsdk.core.scene.message.SceneEventMessage
import com.soulmachines.android.smsdk.core.scene.message.SceneMessageListener
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.ConversationResultEventBody
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.RecognizeResultsEventBody
import com.soulmachines.android.smsdk.core.websocket_message.scene.event.StateEventBody
import java.lang.Math.abs
import android.widget.RelativeLayout

import android.util.TypedValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*


class MainActivity : AppCompatActivity() {

    val TAG = "MainActivity"

    private val PERMISSION_DONT_ASK_AGAIN_FLAG = "PERMISSION_DONT_ASK_AGAIN_FLAG"
    private val PERMISSION_REQUEST_UPDATE_USER_MEDIA = 101
    private val PERMISSIONS_REQUEST = 2

    private var micEnabled: Boolean = true

    lateinit var binding: ActivityMainBinding

    private lateinit var preferences: SharedPreferences

    private var scene: Scene? = null

    private var persona: Persona? = null

    private var showContentClicked = false

    var continueAndDontAskPermissionAgain = false

    private val ran = Random()

    enum class CameraViewDirection {
        Left,
        Center,
        Right
    }
    private var userMedia = UserMedia.None

    //region Setup Activity UI
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        continueAndDontAskPermissionAgain = savedInstanceState?.getBoolean(PERMISSION_DONT_ASK_AGAIN_FLAG) ?: false

        preferences = PreferenceManager.getDefaultSharedPreferences(this as Context)

        Thread.setDefaultUncaughtExceptionHandler(UnhandledExceptionHandler(this))
        goFullScreen()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.connectButton.setOnClickListener {
            connect()
        }
        binding.settingsButton.setOnClickListener {
            openSettingsPage()
        }

        binding.disconnectButton.setOnClickListener{
            disconnect()
        }

        scene = SceneFactory.create(this, userMedia)
        scene!!.setViews(binding.fullscreenPersonaView, binding.pipLocalVideoView)

        scene!!.addDisconnectedEventListener(object : DisconnectedEventListener {
            override fun onDisconnected(reason: String) {
                runOnUiThread { onDisconnectedUI(reason) }
            }
        })


        //example of registering a SceneMessageListener
        scene!!.addSceneMessageListener(object : SceneMessageListener {
            override fun onStateMessage(sceneEventMessage: SceneEventMessage<StateEventBody>) {
                //consume the scene `state` message
            }

            override fun onRecognizeResultsMessage(sceneEventMessage: SceneEventMessage<RecognizeResultsEventBody>) {
                //consume the scene `recognizedResults` message
            }

            override fun onConversationResultMessage(sceneEventMessage: SceneEventMessage<ConversationResultEventBody>) {
                //consume the scene `conversationResult` message
            }

            override fun onUserTextEvent(userText: String) {
                // userText from server received
            }
        })
//        // alternatively, using an adaptor, you can choose to override only one particular message
//        scene!!.addSceneMessageListener(object: SceneMessageListenerAdaptor() {
//            override fun onRecognizeResultsMessage(sceneEventMessage: SceneEventMessage<RecognizeResultsEventBody>) {
//                super.onRecognizeResultsMessage(sceneEventMessage)
//            }
//        })

        scene!!.addPersonaReadyListener(object: PersonaReadyListener {
            override fun onPersonaReady(p: Persona) {
                persona = p
            }
        })

        resetViewUI()

        setupVideoMicToggleButtons()

        setupChangeCameraViewButtons()

        binding.showContentButton.setOnClickListener { v -> toggleContent() }
    }

    private fun openSettingsPage() {
        //display the Preference screen and ask them and ask them to populate the required
        //settings before we can let them connect
        val intent = Intent(this@MainActivity as Context, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(PERMISSION_DONT_ASK_AGAIN_FLAG, continueAndDontAskPermissionAgain)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        continueAndDontAskPermissionAgain = savedInstanceState.getBoolean(PERMISSION_DONT_ASK_AGAIN_FLAG)
    }

    //endregion Setup Activity UI

    //region Scene/Session Connection Usage Example
    private fun connect() {

        if(!hasRequiredConfiguration()) {
            openSettingsPage()
            return
        }

        onConnectingUI()

        // Obtain a JWT token and then connect the Scene
        JWTTokenProvider(this).getJWTToken(
            success = { jwtToken ->
                val connectionUrl = preferences.getString(ConfigurationFragment.CONNECTION_URL, "")!!
                Log.i(TAG, "Connecting to:  `${connectionUrl}`")
                Log.d(TAG, "Using JWT Token `${jwtToken}`")
                //using the obtained JWT token, proceed with connecting the Scene
                connectScene(connectionUrl, jwtToken)
            },
            error = { errorMessage ->
                Log.e(TAG, errorMessage)
                displayAlertAndResetUI(
                    getString(R.string.connection_error),
                    getString(R.string.connection_jwt_error_message)
                )
            })

    }

    private fun connectScene(connectionUrl: String, jwtToken: String) {
        scene?.connect(url = connectionUrl, accessToken = jwtToken)!!.subscribe(
            object : Completion<SessionInfo> {
                override fun onSuccess(result: SessionInfo) {
                    runOnUiThread {
                        onConnectedUI()
                    }
                }

                override fun onError(error: CompletionError) {
                    runOnUiThread {
                        displayAlertAndResetUI(
                            getString(R.string.connection_error),
                            error.getMessage()
                        )
                    }
                }
            }
        )
    }


    private fun hasRequiredConfiguration(): Boolean {

        val useExistingToken = preferences.getBoolean(ConfigurationFragment.USE_EXISTING_JWT_TOKEN, false)
        val url = preferences.getString(ConfigurationFragment.CONNECTION_URL, null)
        val keyName = preferences.getString(ConfigurationFragment.KEY_NAME, null)
        val privateKey = preferences.getString(ConfigurationFragment.PRIVATE_KEY, null)
        val existingToken = preferences.getString(ConfigurationFragment.JWT_TOKEN, null)
        return !url.isNullOrEmpty() && ((useExistingToken && !existingToken.isNullOrEmpty()) || (!keyName.isNullOrEmpty() && !privateKey.isNullOrEmpty()))
    }

    private fun disconnect() {
        onDisconnectingUI()
        scene?.disconnect()
    }

    private fun toggleContent() {
        showContentClicked = !showContentClicked
        binding.contentView.visibility = View.GONE
        if (!showContentClicked) {
            scene!!.getContentAwareness().removeAllContent()
            scene!!.getContentAwareness().syncContentAwareness()
        }
        binding.showContentButton.setImageResource(if (showContentClicked) R.drawable.ic_nocontent else R.drawable.ic_content)
    }

    //endregion Scene/Session Connection Usage Example

    //region Video/Audio Toggle Example

    private fun setupVideoMicToggleButtons() {
        //toggle the state
        binding.microphoneToggle.setOnClickListener {
            it.isSelected = !it.isSelected
            //fire the change event
            videoAudioActiveChanged()
        }
        binding.videoToggle.setOnClickListener {
            it.isSelected = !it.isSelected
            //fire the change event
            videoAudioActiveChanged()
        }

    }

    private fun videoAudioActiveChanged() {
        val isMicEnabled = binding.microphoneToggle.isSelected
        val isVideoEnabled = binding.videoToggle.isSelected
        val requestedUserMedia = when {
            isMicEnabled && isVideoEnabled ->  UserMedia.MicrophoneAndCamera
            !isMicEnabled && isVideoEnabled -> UserMedia.Camera
            isMicEnabled && !isVideoEnabled -> UserMedia.Microphone
            else -> UserMedia.None
        }
        updateUserMediaWithPermission(requestedUserMedia)

    }

    private fun onPermissionGrantedUpdateUserMedia() {
        //Toast.makeText(MainActivity@this, "Using ${userMedia.name}", Toast.LENGTH_SHORT).show()
        scene?.updateUserMedia(this.userMedia)
        //ensure state of buttons and views are in sync with active userMedia
        binding.microphoneToggle.isSelected = this.userMedia.hasAudio
        binding.videoToggle.isSelected = this.userMedia.hasVideo
        binding.pipLocalVideoView.visibility = if(this.userMedia.hasVideo) View.VISIBLE else View.GONE
    }

    //endregion Video/Audio Toggle Example

    //region SpeechRecognizer Usage Example (Mute Button Implementation using SpeechRecognizer - requires MICROPHONE Permission)

    private fun toggleSpeechRecognize() {
        val shouldEnableMic = !micEnabled

        // this example changes the mic mute button state after the async call has succeeded
        if(shouldEnableMic) {
            scene?.getSpeechRecognizer()?.startRecognize()?.subscribe(object : Completion<Unit> {
                override fun onError(error: CompletionError) {
                    Log.w(TAG, "Failed to enable SpeechRecognition.")
                }

                override fun onSuccess(result: Unit) {
                    Log.i(TAG, "SpeechRecognition ON ")
                    runOnUiThread {
                        micEnabled = true
                        binding.microphoneToggle.isSelected = micEnabled
                    }
                }
            })
        } else {
            scene?.getSpeechRecognizer()?.stopRecognize()?.subscribe(object : Completion<Unit> {
                override fun onError(error: CompletionError) {
                    Log.w(TAG, "Failed to turn off SpeechRecognition.")
                }

                override fun onSuccess(result: Unit) {
                    Log.i(TAG, "SpeechRecognition OFF ")
                    runOnUiThread {
                        micEnabled = false
                        binding.microphoneToggle.isSelected = micEnabled
                    }
                }
            })
        }

//        //alternatively, you can just call the async method and change state immediately and assume it is successful as shown below
//        micEnabled = !micEnabled
//        if(micEnabled) {
//            scene?.getSpeechRecognizer()?.startRecognize()
//        } else {
//            scene?.getSpeechRecognizer()?.stopRecognize()
//        }
    }

    //endregion SpeechRecognizer Usage Example (Mute Button Implementation using SpeechRecognizer - requires MICROPHONE Permission)

    //region Scene Usage Example (Change Camera View)


    private fun setupChangeCameraViewButtons() {
        binding.lookToTheLeftButton.setOnClickListener {
            changeCameraView(CameraViewDirection.Left)
        }

        binding.lookToTheCenterButton.setOnClickListener {
            changeCameraView(CameraViewDirection.Center)
        }

        binding.lookToTheRightButton.setOnClickListener {
            changeCameraView(CameraViewDirection.Right)
        }
    }

    private fun changeCameraView(direction: CameraViewDirection) {
        persona?.let { it ->
            showToastMessage("Changing camera view to the $direction")
            Log.i(TAG, "CameraView: $direction")
            it.animateToNamedCameraWithOrbitPan(getNamedCameraAnimationParam(direction))
        }
    }

    private fun getNamedCameraAnimationParam(direction: CameraViewDirection) : NamedCameraAnimationParam {
        var scalar = 0
        if(direction == CameraViewDirection.Left) scalar = 1
        if(direction == CameraViewDirection.Right) scalar = -1
        return NamedCameraAnimationParam(
            cameraName = "CloseUp",
            time = 1.0f, //1 sec
            orbitDegX = 10.0f * scalar,
            orbitDegY = 10f * abs(scalar),
            panDeg = 2f * scalar,
            tiltDeg = 0f)
    }

    //endregion Scene Usage Example (Change Camera View)

    // region Go Fullscreen
    @RequiresApi(11)
    private fun goFullScreen() {
        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = getSystemUiVisibility()
    }
    @TargetApi(19)
    private fun getSystemUiVisibility(): Int {
        var flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        return flags
    }
    //endregion Go Fullscreen

    //region Setup Permissions
    private fun updateUserMediaWithPermission(requestedUserMedia: UserMedia) {
        if ((requestedUserMedia.hasAudio || requestedUserMedia.hasVideo) && ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) + ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            this.userMedia = requestedUserMedia
            val shouldShowRecordAudioPermission = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
            val shouldShowCameraPermission = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
            if (shouldShowRecordAudioPermission || shouldShowCameraPermission) {
                //if we have to show the permissions screen then we have to override this flag
                // so it is skipped
                continueAndDontAskPermissionAgain = false
                showExplanation("Permission Required", "You need to enable permissions.",
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), PERMISSION_REQUEST_UPDATE_USER_MEDIA)
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), PERMISSION_REQUEST_UPDATE_USER_MEDIA)
            }
        } else {
            this.userMedia = requestUserMedia(requestedUserMedia)
            onPermissionGrantedUpdateUserMedia()
        }
    }

    private fun requestUserMedia(requestedUserMedia: UserMedia): UserMedia {
        //check for the permissions and request if necessary
        val missingPermissions = getMissingPermissions()
        var allowedUserMedia = requestedUserMedia
        if(missingPermissions.contains("android.permission.CAMERA") && missingPermissions.contains("android.permission.RECORD_AUDIO"))     {
            allowedUserMedia = UserMedia.None
        } else if(missingPermissions.contains("android.permission.RECORD_AUDIO") && !missingPermissions.contains("android.permission.CAMERA")) {
            //no audio but camera allowed
            allowedUserMedia = UserMedia.Camera
        } else if(missingPermissions.contains("android.permission.CAMERA") && !missingPermissions.contains("android.permission.RECORD_AUDIO")) {
            //no camera but audio allowed
            allowedUserMedia = UserMedia.Microphone
        }
        if(requestedUserMedia.ordinal <= allowedUserMedia.ordinal) {
            return requestedUserMedia
        }
        return allowedUserMedia
    }

    private fun showExplanation(title: String,
                                message: String,
                                permissions: Array<String>,
                                permissionRequestCode: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok
            ) { dialog, id -> requestPermissions(permissions, permissionRequestCode) }
        builder.create().show()
    }

    private fun requestPermission(permissionName: String, permissionRequestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permissionName), permissionRequestCode)
    }

    private fun onPermissionsGranted() {
        connect()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST) {
            val missingPermissions = getMissingPermissions()
            if (missingPermissions.size != 0) {
                // User didn't grant all the permissions. Warn that the application might not work
                // correctly.
                AlertDialog.Builder(this).setMessage(R.string.missing_permissions_try_again)
                    .setPositiveButton(R.string.yes) { dialog, _ ->
                        // User wants to try giving the permissions again.
                        dialog.cancel()
                    }.setNegativeButton(R.string.no) { dialog, _ ->
                        // User doesn't want to give the permissions.
                        dialog.cancel()
                        onPermissionsGranted()
                    }.show()
            } else {
                // All permissions granted.
                onPermissionsGranted()
            }
        } else if(requestCode == PERMISSION_REQUEST_UPDATE_USER_MEDIA) {

            val applicableUserMedia = requestUserMedia(userMedia)

            if(userMedia == applicableUserMedia) {
                this.userMedia = applicableUserMedia
                onPermissionGrantedUpdateUserMedia()
            } else {

                if(continueAndDontAskPermissionAgain) {
                    this.userMedia = applicableUserMedia
                    onPermissionGrantedUpdateUserMedia()
                } else {
                    //display an alert saying some permissions are not enabled and if they wish to continue
                    val dialog = AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setTitle("Missing Permissions")
                        //.setMessage(Html.fromHtml(getString(R.string.permissions_settings_message, applicableUserMedia), Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH))
                        .setMessage(Html.fromHtml(getString(R.string.permissions_settings_message, userMedia, applicableUserMedia), Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton(R.string.yes) { dialog, _ ->
                            dialog.cancel()
                            continueAndDontAskPermissionAgain = false
                            this.userMedia = applicableUserMedia
                            onPermissionGrantedUpdateUserMedia()
                        }.setNegativeButton(R.string.permissions_setting) { dialog, _ ->
                            // User doesn't want to give the permissions.
                            dialog.cancel()
                            continueAndDontAskPermissionAgain = false
                            val intent: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri: Uri = Uri.fromParts("package", getPackageName(), null)
                            intent.setData(uri)
                            startActivity(intent)
                        }
                        .setNeutralButton(R.string.permissions_setting_continue) { dialog, _ ->
                            // User doesn't want to give the permissions.
                            dialog.cancel()

                            continueAndDontAskPermissionAgain = true
                            this.userMedia = applicableUserMedia
                            onPermissionGrantedUpdateUserMedia()

                        }.show()
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun connectRequestingPermissionsIfNeeded() {
        val missingPermissions = getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions, PERMISSIONS_REQUEST)
        } else {
            onPermissionsGranted()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun getMissingPermissions(): Array<String?> {
        val info: PackageInfo = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to retrieve permissions.")
            return arrayOfNulls(0)
        }
        if (info.requestedPermissions == null) {
            Log.w(TAG, "No requested permissions.")
            return arrayOfNulls(0)
        }
        val missingPermissions = ArrayList<String?>()
        for (i in info.requestedPermissions.indices) {
            if (info.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0) {
                missingPermissions.add(info.requestedPermissions[i])
            }
        }
        Log.d(TAG, "Missing permissions: $missingPermissions")
        return missingPermissions.toTypedArray()
    }
    //endregion Setup Permissions

    //region UI Behaviour
    private fun resetViewUI() {
        binding.connectButtonContainer.visibility = View.VISIBLE
        binding.connectButton.isEnabled = true

        binding.disconnectButtonContainer.visibility = View.GONE
        binding.disconnectButton.isEnabled = true

        binding.showContentButton.visibility = View.GONE
        binding.showContentButton.isEnabled = false

        binding.settingsButton.visibility = View.VISIBLE
        binding.settingsButton.isEnabled = true

        binding.microphoneToggle.visibility = View.GONE
        binding.videoToggle.visibility = View.GONE

        binding.cameraViewsContainer.visibility = View.INVISIBLE
    }

    private fun onDisconnectingUI() {
        binding.disconnectButtonContainer.visibility = View.VISIBLE
        binding.disconnectButton.isEnabled = false
        binding.microphoneToggle.visibility = View.GONE
        binding.videoToggle.visibility = View.GONE
    }

    fun onDisconnectedUI(reason: String) {
        Toast.makeText(this@MainActivity, "Disconnected ( ${reason})", Toast.LENGTH_SHORT).show()
        Handler().postDelayed({
            finish()
        }, 100)
    }

   private fun onConnectingUI() {
        Toast.makeText(this, "Connecting, please wait...", Toast.LENGTH_LONG).show()
        binding.connectButtonContainer.visibility = View.VISIBLE
        binding.connectButton.isEnabled = false

        binding.settingsButton.visibility = View.GONE
        binding.settingsButton.isEnabled = false
    }

    fun onConnectedUI() {
        binding.connectButtonContainer.visibility = View.GONE
        binding.disconnectButtonContainer.visibility = View.VISIBLE
        binding.disconnectButton.isEnabled = true

        binding.settingsButton.visibility = View.GONE

        binding.microphoneToggle.visibility = View.VISIBLE
        binding.videoToggle.visibility = View.VISIBLE

        binding.cameraViewsContainer.visibility = View.VISIBLE

        // Determine if Content Awareness is supported. See the Content Awareness section for more information on Content Awareness.
        val isContentAwarenessSupported = scene!!.getFeatures().isFeatureEnabled(FeatureFlags.UI_CONTENT_AWARENESS)
        if (isContentAwarenessSupported) {
            binding.showContentButton.visibility = View.VISIBLE
            binding.showContentButton.isEnabled = true
        }
    }


    private fun displayAlertAndResetUI(title: String, alertMessage: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(alertMessage).setCancelable(false)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.cancel()
                resetViewUI()
            }
            .create().show()
    }

    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun PixelToDip(pixel: Int): Int {
        val r = resources
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, pixel.toFloat(), r.getDisplayMetrics()).toInt()
    }

    private fun showContentView(rawX: Int, rawY: Int, randomW: Int, randomH: Int) {
        binding.contentView.visibility = View.VISIBLE
        binding.contentView.setBackgroundColor(Color.RED)
        val params = RelativeLayout.LayoutParams(randomW, randomH)
        params.leftMargin = PixelToDip(rawX - randomW / 2)
        params.topMargin = PixelToDip(rawY - randomH / 2)
        binding.contentView.layoutParams = params
        binding.contentView.width = PixelToDip(randomW)
        binding.contentView.height = PixelToDip(randomH)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_UP -> if (showContentClicked) {
                val randomW = Math.max(100, ran.nextInt(200))
                val randomH = Math.max(100, ran.nextInt(200))
                showContentView(e.rawX.toInt(), e.rawY.toInt(), randomW, randomH)
                val bounds = Rect(e.rawX.toInt(), e.rawY.toInt(), e.rawX.toInt() + randomW, e.rawY.toInt() + randomH)
                val content: Content = ContentImpl(bounds)
                scene!!.getContentAwareness().addContent(content)
                scene!!.getContentAwareness().syncContentAwareness()
            }
        }
        return super.onTouchEvent(e)
    }

    //endregion UI Behaviour

}
