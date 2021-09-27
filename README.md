
# Kotlin Sample Project

This project shows how to use the Soul Machines Android SDK and pull the library into your own projects. It also shows some of the basic SDK functionality for reference.

# Project Setup
 * Open/import this project in Android Studio as a gradle project.
 * Build the project and it should now download all the dependencies.


# Importing the library

**Add the maven repository**

To import the library into your own project, add the following entries to the `app/build.gradle` file.
```
 repositories {       
	 maven {    
          url "https://sm-maven-repo-bucket.s3.amazonaws.com"
    }    
}
```

**Import the library**

  Add the following dependencies to the `app/build.gradle`
```
 dependencies {        
	 implementation 'com.soulmachines.android:smsdk-core:1.1.0'    
}
```

**Library Documentation**

Documentation for the core sdk is included and should be available here 'app/build/docs/smsdk-core/index.html' after running the task below:

```
./gradlew app:getSmSdkDocumentation
```

In the `app/build.gradle` there is a gradle `documentation` configuration specified as well as a dependency using this
configuration. This is used by the `getSmSdkDocumentation` task to extract the supplied documentation.

```
 dependencies {
	 documentation 'com.soulmachines.android:smsdk-core:1.1.0:docs@zip'
}
```


# Create and connect the Scene
 ## Using View Containers

 * Create **android.view.ViewGroup** container views for the remote persona view (required) and the local video view (optional) on your layout xml where the Scene will be rendered  
 e.g. *activity_main.xml*
 ```
 <FrameLayout      
    android:id="@+id/fullscreenPersonaView"      
    android:layout_width="0dp"      
    android:layout_height="0dp"      
    app:layout_constraintBottom_toBottomOf="parent"      
    app:layout_constraintLeft_toLeftOf="parent"      
    app:layout_constraintRight_toRightOf="parent"      
    app:layout_constraintTop_toTopOf="parent" />
    
<FrameLayout      
    android:id="@+id/pipLocalVideoView"      
    android:layout_width="120dp"      
    android:layout_height="120dp"      
    app:layout_constraintTop_toTopOf="parent"      
    app:layout_constraintLeft_toLeftOf="parent"      
    android:layout_marginLeft="16dp"      
    android:layout_marginTop="16dp" />      
 ```

 * Create a **Scene** object and specify the required **UserMedia** and then set the views on the Scene where you want to render the video feeds. The 2nd parameter (local video view) is optional and can be specified as null.
 ```
 scene = SceneImpl(this, UserMedia.MicrophoneAndCamera)      
 scene!!.setViews(binding.fullscreenPersonaView, binding.pipLocalVideoView)    
 ```

 ## Using a Custom Layout
 * Create a custom layout xml where the Scene video feeds will be rendered. Ensure it has the following child views with the following predefined ids: ***@id/remote_video_view*** and ***@id/local_video_view*** of the type ***org.webrtc.SurfaceViewRenderer***.  
 e.g. *custom_scene_layout.xml*

```
<?xml version="1.0" encoding="utf-8"?> 
<androidx.constraintlayout.widget.ConstraintLayout      
    xmlns:android="http://schemas.android.com/apk/res/android"      
    xmlns:app="http://schemas.android.com/apk/res-auto"      
    xmlns:tools="http://schemas.android.com/tools"      
    android:layout_width="match_parent"      
    android:layout_height="match_parent"      
    android:background="@android:color/black">      
      
    <org.webrtc.SurfaceViewRenderer      
        android:id="@id/remote_video_view"      
        android:layout_width="0dp"      
        android:layout_height="0dp"      
        app:layout_constraintBottom_toBottomOf="parent"      
        app:layout_constraintLeft_toLeftOf="parent"      
        app:layout_constraintRight_toRightOf="parent"      
        app:layout_constraintTop_toTopOf="parent"      
        />      
      
    <org.webrtc.SurfaceViewRenderer      
        android:id="@id/local_video_view"      
        android:layout_width="120dp"      
        android:layout_height="120dp"      
        app:layout_constraintBottom_toBottomOf="parent"      
        app:layout_constraintLeft_toLeftOf="parent"      
		android:layout_marginLeft="16dp"
		android:layout_marginBottom="24dp" /> 
</androidx.constraintlayout.widget.ConstraintLayout> 
```

* Include this layout or embed directly to your Activity's layout. e.g. In your activity's layout file ``` <include android:id="@+id/scene" layout="@layout/custom_scene_layout"/> ```

 * Create a **Scene** object and specify the required **UserMedia**  
and then set the views on the Scene but use the instance of the custom layout you've defined.
 ```
 scene = SceneFactory.create(this, UserMedia.MicrophoneAndCamera)      
 scene!!.setViews(binding.scene, binding.scene)    
 ```
 *In the snippet above, it uses the same custom layout for both the remote and local video feeds, but you can specify a separate one for each as long as you use the correct predefined id for the corresponding child video view*

 ## Connect to a Digital Human (DH) server using a valid web-socket URL and a valid JWT token
 ```
 scene?.connect(      
    url = "wss://dh.soulmachines.cloud",      
    accessToken = "JWT_ACCESS_TOKEN")      
 ```

 ## Connection Result  
On the provided API (e.g. **Scene** and **Persona**), all the asynchronous method calls provide a way such that you can subscribe to the result (whether it was successful or resulted in an error). These methods will return a **Completable/Cancellable** result from which you can subscribe to the result by passing in a **Completion** callback. This interface accepts a generic type parameter that determines the type of the response for a successful result.

Here's an example of a subscription to the scene connection result:

```
scene?.connect(url = getString(R.string.connection_url), accessToken = getString(R.string.connection_access_token))!!
	.subscribe(
	    object : Completion<SessionInfo> {
	        override fun onSuccess(sessionInfo: SessionInfo) {
	            runOnUiThread { onConnectedUI()}
	        }
	        override fun onError(errorMessage: CompletionError) {
	            runOnUiThread {
	                AlertDialog.Builder(this@MainActivity)
	                    .setTitle(getText(R.string.connection_error))
	                    .setMessage(errorMessage?.getMessage() ?: "Unknown error").setCancelable(false)
	                    .setPositiveButton(R.string.ok) { dialog, id ->
	                        dialog.cancel()
	                        resetViewUI()
	                    }
	                    .create().show()
	            }
	        }
	    }
	)
```

# Register event listeners on the Scene

The **Scene** and **Persona** api also provides a way to register event listeners that might be necessary to interact with the digital human. For these event listeners, the pattern is **add{Type}EventListener** and **remove{Type}EventListener***. For both these methods, a **{Type}EventListener** implementation is passed as a parameter.

Here's an example showing a listener for a disconnection event for the Scene:
```
scene!!.addDisconnectedEventListener(object: DisconnectedEventListener {  
    override fun onDisconnected(reason: String) {  
        runOnUiThread {onDisconnectedUI(reason)}  
  }  
})
```


 # Scene Messages
One way to interact with a *Digital Human* is achieved through *Scene Messaging*. This part of the **Scene#addSceneMessageListener** api allows you to register a listener for when these Scene messages are received. To register a message listener, create an instance of a **com.soulmachines.android.smsdk.core.scene.message.SceneMessageListener** or alternatively an instance of the adaptor class **com.soulmachines.android.smsdk.core.scene.message.SceneMessageListenerAdaptor** and only override the specific *Scene Message* you are interested with.
Here is an example using the SceneMessageListener:
```
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
```

***Please review the provided documentation for further information***


 # Persona API
 A **Persona** instance is the api to use to interact with a *Digital Human*. After a successful connection to a scene and the initial 'state' is established, a **Persona** instance can be obtained from the **Scene#getPersonas()** api.

There is also **PersonaReadyListener** you can add to the **Scene** to get notified of when the **Persona** becomes available rather than poll and wait for the 'state' event message.

```
scene!!.addPersonaReadyListener(object: PersonaReadyListener {
    override fun onPersonaReady(p: Persona) {
        persona = p
    }
})
```

An example of usages of the Persona API (see MainActivity#changeCameraView for an example):

```
// make the persona look to the left
val persona = scene.getPersonas().first()
val animation = NamedCameraAnimationParam(
            cameraName = "CloseUp",
            time = 1f, //1 sec
            orbitDegX = 10f,
            orbitDegY = 10f,
            panDeg = 2f,
            tiltDeg = 0f)
persona.animateToNamedCameraWithOrbitPan(animation)
```
## Feature Flags

The `Scene` contains a `Features` object populated shortly after the connection has established. This can be checked to determine whether any DDNA Studio level `FeatureFlags` have been enabled on the `Persona`. Supported `FeatureFlags` are found within the SDK documentation.

```
val isContentAwarenessSupported = scene!!.getFeatures().isFeatureEnabled(FeatureFlags.UI_CONTENT_AWARENESS)
```

## Content Awareness

If the `Persona` has the Content Awareness `FeatureFlag`  enabled in DDNA Studio, classes inheriting from `Content` can be added to the `Scene.getContentAwareness()`. When executing `ContentAwareness.syncContentAwareness()`, these coordinates will be sent to the `Persona`, and it will glance or move out of the way of content as appropriate. 

To add a `Content` item to the `ContentAwareness`, call `Scene.getContentAwareness().addContent(content: Content)`. Content can be removed either by reference or by its String id.

Example:
```
val bounds = Rect(x, y, width, height)
val content: Content = ContentImpl(bounds)
scene!!.getContentAwareness().addContent(content)
scene!!.getContentAwareness().syncContentAwareness()
```
### Content Inheritance

To be added to the `ContentAwareness`, objects need to inherit from `Content`. This ensures that conforming items provide the necessary information for the `Persona` to be aware of their frames within the App or you can call `removeAllContent` to remove all contents.

This information is as follows:
- `getId`: A unique identifier for the content. Content with duplicate ID will replace each other. Note that if the ID matches the id provided to `showcards(id)`, the Persona will gesture at the content.
- `getRect`: A `Rect` of the coordinates the content exists at. This is made up of  `x1, x2, y1, y2`.
- `getMeta`: A dictionary of metadata to associate with the `Content`.

See below for examples.

```
class ContentImpl(private val bounds: Rect) : Content {
    private val id = "object-" + Integer.toString(uniqueId++)
    override fun getId(): String {
        return id
    }

    override fun getMeta(): Map<String, Any>? {
        return null
    }

    override fun getRect(): Rect {
        return bounds
    }

    companion object {
        var uniqueId = 1
    }
}
```
### Example

```
Note that positions are absolute, and should be determined based on the root view when getBounds() is called. 
- '==' and '||' demonstrates the frame of the App Window.
- '--' and '|' demonstrates the frame of the Remote View.
- '<n>' demonstrates a Content instance.

======================
||  --------------  ||
||  | <1> _      |  ||
||  |    / \     |  ||
||  |    \_/  <2>|  ||
||  |   __^__    |  ||
||  |  /     \   |  ||
||  | /       \  |  ||
||  --------------  ||
||        <3>       ||
======================
Approx example coordinates
<1> x1: 100, y1: 100, x2: 150, y2: 150
- As this content is displayed within the frame of the Remote View, if Content Awareness is enabled it will cut to a different position to attempt to prevent the content appearing on top of the Persona. If the Id of the Content is referenced in conversation, the Persona will gesture at the coordinates.

<2> x1: 300, y1: 200, x2: 350, y1: 250
- As this content is displayed within the frame of the Remote View, if Content Awareness is enabled it will cut to a different position to attempt to prevent the content appearing on top of the Persona. If the Id of the Content is referenced in conversation, the Persona will gesture at the coordinates.

<3> x1: 200, y1: 400, x2: 250, y2: 450
- As this coordinate is outside of the Remote View, the Persona will not need to avoid this.

=====================================
||   ____________________          ||
||  |     _             |   <2>    ||
||  |    / \            |          ||
||  |    \_/     <1>    |          ||
||  |   __^__           |          ||
||  |  /     \          |          ||
||  |_/_______\_________|          ||
=====================================
Approx example coordinates
<1> x1: 300, y1: 150, x2: 350, y2: 200
- As this coordinate is possible to overlap the Persona, if Content Awareness is enabled it will cut to a different position to attempt to prevent the content appearing on top of the Persona. If the Id of the Content is referenced in conversation, the Persona will gesture at the coordinates.

<2> x1: 450, y1: 100, x2: 500, y2: 150
- As this coordinate is outside of the Remote View, the Persona will not need to avoid this.
```
