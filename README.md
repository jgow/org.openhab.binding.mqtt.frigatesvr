# OpenHAB Frigate SVR Binding

This is a comprehensive binding for the Frigate SVR system (https://docs.frigate.video/). It allows access to all configured
cameras, and realtime event information from the cameras can be used in rules. It tracks Frigate server status and can alert
if the Frigate server goes offline.

_For build instructions see the 'Building' section below_

The binding supports:

- multiple Frigate server instances
- tracking and notification of Frigate server status
- autodiscovery of your cameras as listed in the Frigate configuration
- comprehensive camera event notification on a per-camera basis
- ability to change individual camera settings (such as turning recording and object detection on/off, enabling 'improve contrast' etc) via suitable channels.
- availability of JPEG snapshots (with boundary boxes) of last-detected object to OpenHAB as a channel.
- statistics information on a per-camera basis (fps etc).
- an API forwarder, allowing a local UI to use the Frigate server's HTTP API via a local endpoint on the OpenHAB server.
- a streaming mechanism, allowing video for the 'birdseye' view and individual camera feeds to be viewed on the OpenHAB API.

## Philosophy

The design philosophy behind this binding is to allow for three main areas of functionality

- OpenHAB access to the useful and considerable status and event information from the cameras attached to the Frigate server, to allow rules to be triggered based upon changes to the streams as detected by Frigate. 
- Per-camera configuration (such as turning motion detect on/off)
- A forwarder mechanism to allow API calls to the Frigate server via an endpoint on the openHAB instance.
- A streaming mechanism allowing video from the RTSP feeds of server 'birdseye' view and individual camera feeds to be visible on a UI widget via a URL on the local instance.

### Events and channels
Frigate detection events fall into three types: 'new', 'update', and 'end'. When an event occurs, it is assigned a unique ID and an event is posted. The binding picks this up and farms out the useful information into a set of channels.

Frigate sends events as a delta - the event packet contains information pertaining to both the previous state (before the event occurred - denoted 'previous' in the camera channel descriptions) and information relating to the current state (denoted 'current' in the camera channel descriptions). This allows openHAB rules to make an intelligent decision based upon changes to the 'picture' seen by the cameras, with Frigate itself responsible for all the detection and image processing. 

The frigateSVR binding provides a *lot* of openHAB channels that can be used in a multitude of ways. Further information can be obtained through perusal of the Frigate documentation - the channel mapping is logical and follows the Frigate specifications - there should be no surprises. However, the architecture of this binding is slightly different from usual and the following section provides an overview.

### API forwarder

An API forwarder is available, allowing a UI widget to communicate with the Frigate server via a URL to the local openHAB instance (see channel fgAPIForwarderURL)

### Streaming

Prior to binding version 1.5, the binding did not handle video in any way. However, starting with binding version 1.5 - video streams are now available directly from a servlet on the openHAB instance. This makes use of the Frigate rtsp stream exports, and internally the binding uses a very similar mechanism to the 'ipcamera' binding to allow them to be rendered locally. That having been said, it is recommended that this facility be used only for low resolution streams as otherwise network and CPU loads may become intolerable.

Frigate does all of the 'heavy lifting', including the object detection. An openHAB UI widget (e.g. oh-video) can read the stream directly from a URL provided on a channel to display real time feeds.

## Architecture

This binding operates a little differently to some other bindings - this is down to the nature of the split API provided by Frigate. Most of the useful information is provided by Frigate across an MQTT channel; some via an HTTP-based API. Just to make things awkward, there is no easy way to link the incoming MQTT messages with a specific Frigate instance. There is the 'clientID' field in the Frigate configuration, but this requires a-priori knowledge and limits autoconfiguration/discovery somewhat. Similarly, without aggressive network scanning it would be difficult to automatically detect Frigate servers, even on the local network.

Thus, this binding sits on top of the MQTT bridge binding - as most of the key communication is via MQTT. As openHAB provides a useful HTTP client, this architecture saves implementing a separate MQTT client with an additional connection to the broker while preserving the links between the instance of the server 'thing' and the camera 'things' handled by it.

So this binding provides two 'Thing' types that are able to talk to each other - a server 'Thing' and a camera 'Thing'. The relationship between the two is one to many: i.e. one server 'Thing' can support multiple camera 'Things'. This represents the Frigate installation: the server 'Thing' represents the Frigate server instance itself (but not the cameras). The camera 'Thing' represents a Frigate camera, and each Frigate server 'thing' can support one or more Frigate camera 'Things'.

First a server 'Thing' must be instantiated, configured and brought online. As a minimum, this just needs the URL to the Frigate server to configure it. Once online, camera 'Things' can be instantiated, best by autodiscovery (as this automatically configures the camera 'Thing' requiring no further action by the user to configure it), or manually as below.

After this, the cameras are up and running; items can be linked and rules written.

## Requirements

- a configured and running Frigate installation, either local or remote, with its HTTP API accessible from your OpenHAB instance.
- a fully configured MQTT broker (e.g. Mosquitto) configured on a server accessible to openHAB and to which your Frigate installation is configured to post messages.
- the OpenHAB MQTT broker binding installed, and configured to communicate with the above MQTT broker.

## Supported Things

The FrigateSVR binding supports two 'things':

- a frigateServer 'thing'
- a frigateCamera 'thing'

**It is important to note** that this binding initially requires a frigateServer 'thing' to be instantiated, one per Frigate server (it is possible to have multiple frigateServer 'things' each pointing to a different Frigate server. In this case, the 'clientID' parameter must be set in the Frigate configuration to allow the servers to be discriminated.

Once a frigateServer 'thing' is instantiated, then the frigateCamera 'things' can be instantiated. The easiest way to do this is via a discovery scan (see below). FrigateCamera 'things' can be instantiated manually as well if the ThingID of the relevant frigateServer 'thing' is known and can be entered into the frigateCamera configuration.

_It is important to note that frigateCamera 'things' can not be instantiated alone without a server 'thing' - there must be a frigateServer 'thing' available to which the frigateCamera is bound (strictly speaking, a frigateCamera 'thing' can be instantiated, but it will remain offline until bound to a frigateServer 'thing' that supports this camera)._

## Discovery

Autodiscovery of your cameras (manually through use of the 'scan' button on the UI) is supported.

You must first manually instantiate and configure a frigateServer 'thing'.

To make use of the autodiscovery of cameras, when creating a new 'thing' first select the MQTT broker bridge. Then select the frigateServer 'thing' you have instantiated as above (it is impossible, practically, to automatically scan for Frigate **servers** without aggressive network probing). In the configuration panel for the frigateServer 'thing', select the bridge to the MQTT binding, and fill in the 'Frigate server URL' configuration field with the URL to your Frigate server. If you have multiple Frigate instances with the Frigate 'clientID' parameter set, then enter the relevant client ID in the 'Frigate server client ID' field. The keepalive interval can remain at the default to start with. Then complete the creation of the thing.

The Frigate server 'thing' will appear in your list of 'things'.

Providing your Frigate server is accessible, within 5-8 seconds, your Frigate server 'thing' should be listed green and 'online'. Once this is the case, return to the 'Things' menu and click the '+' button. Select the MQTT broker from the list and hit the red 'Scan Now' button. The cameras attached to the Frigate server will appear in the list. These can then be instantiated in the normal way.

## 'Thing' Configuration

There are two 'Things' required to be instantiated, starting with a frigateSVRserver, then one or more frigateSVRcameras. If using autodiscovery for the cameras, then only the server 'Thing' needs to be configured manually

### `frigateSVR Server` 'Thing' Configuration

| Name                           | Type    | Description                                                           | Default                                | Required | Advanced |
|--------------------------------|---------|-----------------------------------------------------------------------|----------------------------------------|----------|----------|
| serverURL                      | text    | URL to the running Frigate server                                     | N/A                                    | yes      | no       |
| serverClientID                 | text    | 'clientID' parameter in Frigate config                                | N/A                                    | no       | no       |
| serverKeepAlive                | integer | Interval the device is polled in sec.                                 | 5                                      | yes      | no       |
| enableAPIForwarder             | boolean | Enable the Frigate API forwarder                                      | true                                   | yes      | no       |
| enableStream                   | boolean | Enable the internal stream server                                     | true                                   | yes      | no       |
| streamWhitelist                | text    | List of IPs allowed to connect                                        | DISABLE                                | no       | yes      |    
| ffmpegLocation                 | text    | Location of ffmpeg binary                                             | /usr/bin/ffmpeg                        | yes      | yes      |
| ffMJPEGStartProducerOnLoad     | text    | Start ffmpeg for MJPEG streams when binding started                   | false                                  | yes      | yes      |
| ffMJPEGTranscodeCommands       | text    | Commands for ffmpeg transcode section for MJPEG streams               | -q:v 5 -r 2 -vf scale=640:-2 -update 1 | yes      | yes      |
| ffHLSStartProducerOnLoad       | text    | Start ffmpeg on binding start for HLS streams                         | false                                  | yes      | yes      |
| ffHLSTranscodeCommands         | text    | Commands for ffmpeg transcode section for HLS streams                 | -acodec copy -vcodec copy              | yes      | yes      |
| ffDASHStartProducerOnLoad      | text    | Start ffmpeg on binding start for DASH streams                        | false                                  | yes      | yes      |
| ffDASHTranscodeCommands        | text    | Commands for ffmpeg transcode section for DASH streams                | -acodec copy -vcodec copy              | yes      | yes      |
| ffDASHPackageCommands          | text    | Commands for ffmpeg stream package section for DASH                   | -seg_duration 1 -streaming 1           | yes      | yes      |
| ffMinFramesToStart             | integer | Minimum number of frames processed by ffmpeg to indicate stream start | 10                                     | yes      | yes      |
| ffKeepalivesBeforeExit         | integer | Number of keepalives to wait before terminating ffmpeg                | 2                                      | yes      | yes      |
| ffTempDir                      | text    | Working directory for stream data                                     | openHAB user data area                 | no       | yes      |

#### Notes:

- serverURL: In most instances, only this needs to be added manually. This should be the base URL to the Frigate server.
- serverClientID: this should be set to the same client ID as is set in your Frigate configuration. This allows support of multiple Frigate instances
- serverKeepAlive: this is the keepalive interval between calls to the Frigate HTTP API (to evaluate Frigate server state)
- enableAPIForwarder: this will enable the API forwarder. All aspects of the Frigate HTTP API are available on the endpoint (specified by the channel fgAPIForwarderURL) except the MJPEG debug streams.
- enableStream: if there are no UI streams requested, there is virtually no additional CPU or network load increase by setting 'enableStream' to true, unless corresponding 'ff***StartProducerOnLoad flags are set.
- the 'streamWhiteList' is a space-separated string of IP addresses that will be accepted by the stream server. Set to 'DISABLE' to disable completely, allowing connections from anywhere.
- ff***StartProducerOnLoad: if these parameters are set true, then the relevant ffmpeg processes will be started with the binding, rather than on demand when a UI element requests the stream. For non-transcoding streams such as DASH and HLS, the CPU impact is minimal. However, the network impact should be considered. Note that starting on load will delay the onlining of the 'things' by the time it takes to start the streams. If set false, there will be a short delay when the stream is requested to allow the ffmpeg processes to start.
- ffmpeglocation: this refers to the location of the installed ffmpeg binary on the device running the openHAB instance. **A relatively recent ffmpeg is required**
- ff***TranscodeCommands: these are ffmpeg commands for the transcode section of the ffmpeg command string for the relevant stream type
- ff***PackageCommands: these are ffmpeg command line options for the stream package section of the ffmpeg command string (after -f <format>).
- ffMinFramesToStart: The stream start sequence looks for files to be present, and also for the minimum number of frames processed by ffmpeg to be equal or greater to this quantity before the stream is considered started.
- ffKeepalivesBeforeExit: If the ff***StartProducerOnLoad is set false, this parameter specifies how many keepalives should elapse without a stream file request being received before the ffmpeg process is shut down.
- ffTempDir: this is the working directory for the served streams. This is by default the openHAB user data area, but could be set to a ramdisk (e.g. /dev/shm) to improve performance. The files created are deleted when a stream is shut down, and the streams are organized to rotate and not fill the disk.

### `frigateSVR Camera` 'Thing' Configuration

| Name                           | Type    | Description                                                           | Default                                | Required | Advanced |
|--------------------------------|---------|-----------------------------------------------------------------------|----------------------------------------|----------|----------|
| serverID                       | text    | Thing ID of bound Server 'Thing'                                      | N/A                                    | yes      | no       |
| cameraName                     | text    | Camera name of Frigate camera                                         | N/A                                    | yes      | no       |
| enableStream                   | boolean | Enable the internal stream server                                     | true                                   | no       | no       |
| ffmpegCameraNameOverride       | text    | Name of an alternate RTSP stream from Frigate                         | empty                                  | no       | yes      |
| ffMJPEGStartProducerOnLoad     | text    | Start ffmpeg for MJPEG streams when binding started                   | false                                  | yes      | yes      |
| ffMJPEGTranscodeCommands       | text    | Commands for ffmpeg transcode section for MJPEG streams               | -q:v 5 -r 2 -vf scale=640:-2 -update 1 | yes      | yes      |
| ffHLSStartProducerOnLoad       | text    | Start ffmpeg on binding start for HLS streams                         | false                                  | yes      | yes      |
| ffHLSTranscodeCommands         | text    | Commands for ffmpeg transcode section for HLS streams                 | -acodec copy -vcodec copy              | yes      | yes      |
| ffDASHStartProducerOnLoad      | text    | Start ffmpeg on binding start for DASH streams                        | false                                  | yes      | yes      |
| ffDASHTranscodeCommands        | text    | Commands for ffmpeg transcode section for DASH streams                | -acodec copy -vcodec copy              | yes      | yes      |
| ffDASHPackageCommands          | text    | Commands for ffmpeg stream package section for DASH                   | -seg_duration 1 -streaming 1           | yes      | yes      |
| ffMinFramesToStart             | integer | Minimum number of frames processed by ffmpeg to indicate stream start | 10                                     | yes      | yes      |
| ffKeepalivesBeforeExit         | integer | Number of keepalives to wait before terminating ffmpeg                | 2                                      | yes      | yes      |
| ffTempDir                      | text    | Working directory for stream data                                     | openHAB user data area                 | yes      | yes      |

#### Notes:

- for the camera 'thing', both 'serverID' and 'cameraName' configuration fields are completed automatically if autodiscovery is used. If not, then the 'serverID' should be the ID of the instantiated and running frigateSVRServer 'thing' supporting the camera. The 'Camera name' is the name of the camera as it appears in the Frigate config, or the Frigate UI.
- the camera streams 'whitelist' is passed in from the server 'thing' to which the camera 'thing' is bound.
- the ffmpeg binary location is passed in from the server 'thing'.
- the 'ffmpegCameraNameOverride' parameter is useful. If you have configured Frigate's cameras with multiple streams - say a high resolution stream for recording on Frigate and a lower resolution for detection, these streams may have a different name to the camera name. For example, using this field, you could pull in a substream running at a lower frame rate for display in openHAB to reduce network resources and CPU load. If you pass in the detection stream rather than the high resolution stream, the CPU and network load will be **much** lower than if you use the high resolution stream.
- Similarly, if your restream from Frigate has a different name to your camera, the 'ffmpegCameraNameOverride' field is where you can specify it.
- ff***StartProducerOnLoad: if these parameters are set true, then the relevant ffmpeg processes will be started with the binding, rather than on demand when a UI element requests the stream. For non-transcoding streams such as DASH and HLS, the CPU impact is minimal. However, the network impact should be considered. Note that starting on load will delay the onlining of the 'things' by the time it takes to start the streams. If set false, there will be a short delay when the stream is requested to allow the ffmpeg processes to start.
- ffmpeglocation: this refers to the location of the installed ffmpeg binary on the device running the openHAB instance. **A relatively recent ffmpeg is required**
- ff***TranscodeCommands: these are ffmpeg commands for the transcode section of the ffmpeg command string for the relevant stream type
- ff***PackageCommands: these are ffmpeg command line options for the stream package section of the ffmpeg command string (after -f <format>).
- ffMinFramesToStart: The stream start sequence looks for files to be present, and also for the minimum number of frames processed by ffmpeg to be equal or greater to this quantity before the stream is considered started.
- ffKeepalivesBeforeExit: If the ff***StartProducerOnLoad is set false, this parameter specifies how many keepalives should elapse without a stream file request being received before the ffmpeg process is shut down.
- ffTempDir: this is the working directory for the served streams. This is by default the openHAB user data area, but could be set to a ramdisk (e.g. /dev/shm) to improve performance. The files created are deleted when a stream is shut down, and the streams are organized to rotate and not fill the disk.


## Channels

### `frigateSVR Server` 'Thing' Channels

| Channel            | Type   | Read/Write  | Description                                                                                           |
|--------------------|--------|-------------|-------------------------------------------------------------------------------------------------------|
| fgAPIVersion       | String | R/O         | Version of the Frigate API being used                                                                 |
| fgUI               | String | R/O         | URL to the Frigate UI for this server (useful for openHAB UI widgets)                                 |
| fgAPIForwarderURL  | String | R/O         | URL to the API forwarder - allowing UI widgets to access the Frigate API from a local server instance |
| fgBirdseyeURL      | String | R/O         | URL to the openHAB stream for the Frigate 'birdseye' view (if enabled)                                |

#### Notes

- the 'fgAPIVersion' is the version returned by the Frigate API
- 'fgUI' is the base URL of the Frigate server being used
- 'fgBirdseyeURL': if the Frigate server is set up to restream the 'birdseye' view (if in the Frigate config, 'restream: true' is set in the 'birdseye' section), and if the 'enableStream' configuration parameter on the frigateSVR server 'thing' is set true, then a stream of the 'birdseye' view can be had at this URL. If Frigate is not configured to provide this, or the 'enableStream' parameter is set to off, then this URL will be blank.


### `frigateSVR Camera` 'Thing' Channels

| Channel               | Type     | Read/Write  | Description                                                   |
|---------------------- |----------|-------------|---------------------------------------------------------------|
| fgCameraFPS           | Number   | R/O         | Configured FPS for this camera                                |
| fgCameraProcessFPS    | Number   | R/O         | Actual processing FPS for this camera                         |
| fgCameraSkippedFPS    | Number   | R/O         | Skipped FPS for this camera                                   |
| fgCameraDetectionFPS  | Number   | R/O         | Detection FPS for this camera                                 |
| fgDetectionState      | Switch   | R/W         | Turn object detection on/off                                  |
| fgRecordingState      | Switch   | R/W         | Turn recording on/off                                         |
| fgSnapshotState       | Switch   | R/W         | Turn snapshots on/off                                         |
| fgMotionState         | Switch   | R/W         | Turn snapshots on/off                                         |
| fgContrastState       | Switch   | R/W         | Turn 'improve contrast' on/off                                |
| fgMotionThreshold     | Number   | R/W         | Read/set motion detection threshold                           |
| fgMotionContourArea   | Number   | R/W         | Read/set motion contour area                                  |
| fgMotionDetected      | Contact  | R/O         | Motion detected                                               |
| fgStreamURL           | String   | R/O         | URL to local camera streams for UIs (if enabled)              |
| fgLastSnapshotObject  | String   | R/O         | Type of object detected in last snapshot                      |
| fgLastSnapshot        | Image    | R/O         | Snapshot of last detected object                              |
| fgCurrentEventType    | String   | R/O         | Current event type ('new', 'update' or 'end')                 |
| fgEventClipURL        | String   | R/O         | Full URL to the clip of the current event                     |
| fgPrevFrameTime       | DateTime | R/O         | Prior to event: Frame time prior to event                     |
| fgPrevSnapshotTime    | DateTime | R/O         | Prior to event: Time of snapshot                              |
| fgPrevLabel           | String   | R/O         | Prior to event: Detected entity                               |
| fgPrevSubLabel        | String   | R/O         | Prior to event: Sublabel of detected entity                   |
| fgPrevTopScore        | Number   | R/O         | Prior to event: Top score                                     |
| fgPrevFalsePositive   | Contact  | R/O         | Prior to event: was this a false positive?                    |
| fgPrevStartTime       | DateTime | R/O         | Prior to event: Event start time                              |
| fgPrevEndTime         | DateTime | R/O         | Prior to event: Event end time                                |
| fgPrevScore           | Number   | R/O         | Prior to event: Score                                         |
| fgPrevBox             | String   | R/O         | Prior to event: Detection box coords (given as array [..])    |
| fgPrevArea            | String   | R/O         | Prior to event: Detection box area (given as array [a,b,c,d]) |
| fgPrevRatio           | Number   | R/O         | Prior to event: Detected ratio                                |
| fgPrevRegion          | String   | R/O         | Prior to event: Detected regions                              |
| fgPrevCurrentzone     | String   | R/O         | Prior to event: Detected zones (given as array [..])          |
| fgPrevEnteredzone     | String   | R/O         | Prior to event: Entered zones (given as array [..])           |
| fgPrevHasSnapshot     | Contact  | R/O         | Prior to event: is a snapshot available?                      |
| fgPrevHasClip         | Contact  | R/O         | Prior to event: is a clip available?                          |
| fgPrevStationary      | Contact  | R/O         | Prior to event: is object stationary?                         |
| fgPrevMotionlessCount | Number   | R/O         | Prior to event: Number of motionless frames                   |
| fgPrevPositionChanges | Number   | R/O         | Prior to event: Number of position changes                    |
| fgCurFrameTime        | DateTime | R/O         | Current event: Frame time prior to event                      |
| fgCurSnapshotTime     | DateTime | R/O         | Current event: Time of snapshot                               |
| fgCurLabel            | String   | R/O         | Current event: Detected entity                                |
| fgCurSubLabel         | String   | R/O         | Current event: Sublabel of detected entity                    |
| fgCurTopScore         | Number   | R/O         | Current event: Top score                                      |
| fgCurFalsePositive    | Contact  | R/O         | Current event: was this a false positive?                     |
| fgCurStartTime        | DateTime | R/O         | Current event: Event start time                               |
| fgCurEndTime          | DateTime | R/O         | Current event: Event end time                                 |
| fgCurScore            | Number   | R/O         | Current event: Score                                          |
| fgCurBox              | String   | R/O         | Current event: Detection box coords (given as array [..])     |
| fgCurArea             | String   | R/O         | Current event: Detection box area (given as array [a,b,c,d])  |
| fgCurRatio            | Number   | R/O         | Current event: Detected ratio                                 |
| fgCurRegion           | String   | R/O         | Current event: Detected regions                               |
| fgCurCurrentzone      | String   | R/O         | Current event: Detected zones (given as array [..])           |
| fgCurEnteredzone      | String   | R/O         | Current event: Entered zones (given as array [..])            |
| fgCurHasSnapshot      | Contact  | R/O         | Current event: is a snapshot available?                       |
| fgCurHasClip          | Contact  | R/O         | Current event: is a clip available?                           |
| fgCurStationary       | Contact  | R/O         | Current event: is object stationary?                          |
| fgCurMotionlessCount  | Number   | R/O         | Current event: Number of motionless frames                    |
| fgCurPositionChanges  | Number   | R/O         | Current event: Number of position changes                     |

#### Notes

- 'Current event' and 'Prior to event' channels are updated with fgCurrentEventType. This ensures consistency of information passed to event handlers - there should be no 'stale' information left in any of the 'Cur' or 'Prev' channels. Note also that some of these values may change to NULL if the value on the Frigate server side is NULL. Thus, rules that wish to interrogate multiple 'cur' or 'prev' channels should trigger on changes to 'fgCurrentEventType' as this channel is updated once all other event channels have been updated.
- the event and control channels follow the Frigate documentation and there should be no surprises here.
- 'fgMJPEGURL': if the configuration parameter 'enableStream' is set true, if Frigate is configured to restream cameras and if the stream is on either 'cameraName' or 'ffmpegCameraNameOverride', then 'fgMJPEGURL' will provide a URL to a locally restreamed feed of MJPEG. Note that if you select a high resolution stream from Frigate, this could significantly increase CPU and network load as the local instance will have to transcode the stream. Consider using the detection substreams at lower frame rates - these are often sufficient and will result in much lower CPU loads.

## Writing rules for FrigateSVR cameras

An example of how a rule can be written to use the event information follows. This example updates an item with the number of persons currently present in the field of regard of a Frigate camera, whose Thing ID is 'Camera-Main' and is triggering on the channel fgCurrentEventType, bound to item 'Camera__Main_Current_Event_Type'

Note that the detected entity string is supplied by Frigate wrapped in quotes. I may modify the binding later to strip these off.

```
configuration: {}
triggers:
  - id: "1"
    configuration:
      itemName: Camera__Main_Current_Event_Type
    type: core.ItemStateUpdateTrigger
conditions: []
actions:
  - inputs: {}
    id: "2"
    configuration:
      type: application/javascript
      script: >-

        if(this.map == undefined) {
          this.map=new Map();
        }
        var entity=items.getItem("Camera__Main_Current_Detected_Entity").state;
        var type=items.getItem("Camera__Main_Current_Event_Type").state;
        var id=items.getItem("Camera__Main_Current_Event_ID").state;

        console.log("Detected entity:" + entity);

        if (entity == "\"person\"") {
          console.log("Person detected: event type " + type);
          switch(type) {
            case "update":
            case "add":     this.map.set(id,entity);
                            break;
            case "end":     this.map.delete(id);
                            break;
          }
        }

        items.getItem("PersonCount").postUpdate(this.map.size);
    type: script.ScriptAction
```

## Displaying Frigate camera video streams on OpenHAB UI - an example

Versions of this binding prior to 1.5 do not handle video - but these older versions can be used with the 'ipcamera' binding to transcode restreamed video from Frigate. This was not satisfactory as it required installation of a lot of additional 'stuff' just to get the video proxy.
If you are reading this, then the version in this tree **does** support native video and should do so with the minimum of configuration:

- Ensure you have a version of ffmpeg installed on your OpenHAB box that has access to the appropriate codecs (e.g. h264) - for example I use openSuSE, so had to install the 'Packman' version of ffmpeg in order to get the necessary codecs. There should be a similar source of codecs for your distribution of choice. Note the path of ffmpeg (usually /usr/bin/ffmpeg under Linux).
- For cameras:
    - Frigate exports RTSP streams for each camera on `http://<frigate-server>:8554/<stream-name>` The binding will by default look for a stream where <stream-NAME> is equal to the cameraName. If the Frigate configuration differs, ensure the desired Frigate <stream-name> is inserted in the 'ffmpegCameraNameOverride' parameter on the camera 'thing'.
    - Ensure that the streaming is turned on using the 'enableStream' parameter on the camera 'thing'.
    - On the frigateSVR server 'thing' check the 'ffmpegLocation' parameter points to the ffmpeg binary.
    - If the camera is online, the 'fgStreamURL' channel on the camera 'thing' should contain a URL. To access the relevant stream, append an extension to this URL (.m3u8 for HLS, .mpd for DASH,  no extension for MJPEG) and this forms a URL to which you can point your UI widget. You should then see the video stream in the UI widget. This will be a 'clean' video stream from the camera - there will be no overlays on object detection etc.
    - The **snapshots** exposed by the frigateSVR binding  _will_  show the overlays on detection of objects.
- For the 'birdseye' view:
    - Ensure this is turned on in Frigate by having 'restream: true' in the 'birdseye' section of the Frigate config.
    - On the server 'thing', ensure 'enableStream' is on and that the ffmpeg binary path is correct in 'ffmpegLocation'.
    - If the stream is available, the channel 'fgBirdseyeURL' should contain a URL to point a UI widget at to display the Frigate birdseye view.
    - The birdseye view is quite a good mechanism to look at all cameras in openHAB while minimizing CPU and network load.

### UI example

```
- component: oh-video
  config:
      url: =items.frigateSVR_Server_Birdseye_stream_URL.state + ".m3u8"
```

# Building

Conventional openHAB wisdom is to fork the complete add-on repository and work from there - but Java is noisy and busy enough without duplicating yet more 'stuff' that I am not planning to work on. There are also issues then in making it clear on initial visit to this repository exactly what this repository contains. So I am managing here only the part I am actually developing. Thus: the build process is as follows:

- Change to an empty directory in which you want to build.
- Clone the openhab-addons repository from openHAB
- Using 'git submodule', import the FrigateSVR repository into the 'bundles' subdirectory of the openhab-addons directory, keeping the org.openhab.binding.mqtt.frigatesvr directory name (I guess you could use 'git subtree' if you wished?).
- Open the openhab-addons/bundles/pom.xml file in an editor:
  - Firstly check the version number at the top of the file in the `<parent>` hierarchy - do not change this but copy it somewhere.
  - In the `<modules>` section, add `<module>org.openhab.binding.mqtt.frigatesvr</module>`
- Open the bundles/org.openhab.binding.mqtt.frigatesvr/pom.xml
  - Check the version in the `<parent>` hierarchy. If it is not the same as that noted earlier from the bundles/pom.xml file, then change it so that it is (it most likely will not be as openHAB develops). If the numbers are not the same you will get a build error. Save file if changed
- In the openhab-addons directory, start the build.

On Linux, the steps are:

- `git clone https://github.com/openhab/openhab-addons.git`
- `cd openhab-addons/bundles`
- `git submodule add https://github.com/jgow/org.openhab.binding.mqtt.frigatesvr.git`
- `cd ..`
- `vi bundles/pom.xml`   (note the build version, add the `<module>org.openhab.binding.mqtt.frigatesvr</module>` to the module section and save.
- `vi bundles/org.openhab.binding.mqtt.frigatesvr/pom.xml` (check that the version matches above, change to ensure that it is)
- `mvn clean install -pl :org.openhab.binding.mqtt.frigatesvr`

Once the build is complete, the .jar will be found in bundles/org.openhab.binding.mqtt.frigatesvr/target. Copy this .jar to the add-ons directory on your running openHAB environment. Enjoy.

# Releases

If you do not want to build it yourself, I do provide occasional snapshot releases as .jar builds. See 'Releases' for more details

