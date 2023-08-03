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
- ability to change individual camera settings (such as turning motion detect on/off, enabling 'improve contrast' etc.)
- availability of snapshots of last-detected object to OpenHAB as a channel.
- statistics information on a per-camera basis (fps etc).
- (coming soon):
    - server memory state (allowing OpenHAB to trigger rules/notifications if the Frigate server starts to run out of resources)

## Philosophy

The design philosophy behind this binding is to allow OpenHAB access to the useful and considerable status and event information from the cameras attached to the Frigate server, to allow rules to be triggered based upon changes to the streams as detected by Frigate.

Frigate detection events fall into three types: 'new', 'update', and 'end'. When an event occurs, it is assigned a unique ID and an event is posted. The binding picks this up and farms out the useful information into a set of channels.

Frigate sends events as a delta - the event packet contains information pertaining to both the previous state (before the event occurred - denoted 'previous' in the camera channel descriptions) and information relating to the current state (denoted 'current' in the camera channel descriptions). This allows openHAB rules to make an intelligent decision based upon changes to the 'picture' seen by the cameras, with Frigate itself responsible for all the detection and image processing. 

The binding does not process or proxy video streams in and of itself; Frigate does all of the 'heavy lifting', including the object detection. Similarly, the management, viewing and downloading of actual video clips is more appropriately carried out by a UI - either Frigate's own UI or a UI widget integrated into OpenHAB - Frigate provides a rich REST API with which to facilitate such a design and this should be leveraged directly by a UI designer should an OpenHAB UI element wish to incorporate these features. It is clearly necessary for the openHAB instance to be able to communicate with the Frigate instance, however for UI purposes and depending on the topology of your network it may be desirable to pass part of the Frigate API through a reverse proxy so that it is accessible remotely. This binding does *not* provide these features either, as there is much more appropriate open-source software available to provide this if needed (e.g. one of the Apache web servers).

That having been said, the frigateSVR binding provides a *lot* of openHAB channels that can be used in a multitude of ways. Further information can be obtained through perusal of the Frigate documentation - the channel mapping is logical and follows the Frigate specifications - there should be no surprises. However, the architecture of this binding is slightly different from usual and the following section provides an overview.

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

To make use of this, when creating a new 'thing' first select the MQTT broker bridge. Then select the frigateServer 'thing' (it is impossible, practically, to automatically scan for Frigate **servers** without aggressive network probing). In the configuration panel for the frigateServer 'thing', select the bridge to the MQTT binding, and fill in the 'Frigate server URL' configuration field with the URL to your Frigate server. If you have multiple Frigate instances with the Frigate 'clientID' parameter set, then enter the relevant client ID in the 'Frigate server client ID' field. The keepalive interval can remain at the default to start with. Then complete the creation of the thing.

The Frigate server 'thing' will appear in your list of 'things'.

Providing your Frigate server is accessible, within 5-8 seconds, your Frigate server 'thing' should be listed green and 'online'. Once this is the case, return to the 'Things' menu and click the '+' button. Select the MQTT broker from the list and hit the red 'Scan Now' button. The cameras attached to the Frigate server will appear in the list. These can then be instantiated in the normal way.

## 'Thing' Configuration

There are two 'Things' required to be instantiated, starting with a frigateSVRserver, then one or more frigateSVRcameras. If using autodiscovery for the cameras, then only the server 'Thing' needs to be configured manually

### `frigateSVR Server` 'Thing' Configuration

| Name                      | Type    | Description                           | Default | Required | Advanced |
|---------------------------|---------|---------------------------------------|---------|----------|----------|
| Frigate server URL        | text    | URL to the running Frigate server     | N/A     | yes      | no       |
| Frigate server client ID  | text    | 'clientID' parameter in Frigate       | N/A     | no       | no       |
| Server keepalive interval | integer | Interval the device is polled in sec. | 5       | yes      | yes      |

In most instances, only the 'Frigate server URL' needs to be added manually. This should be the base URL to the Frigate server.

### `frigateSVR Camera` 'Thing' Configuration

| Name                           | Type    | Description                       | Default | Required | Advanced |
|--------------------------------|---------|-----------------------------------|---------|----------|----------|
| Frigate camera server Thing ID | text    | Thing ID of bound Server 'Thing'  | N/A     | yes      | no       |
| Camera name                    | text    | Camera name of Frigate camera     | N/A     | yes      | no       |

Note that for the camera Thing, both configuration fields are completed automatically if autodiscovery is used. If not, then the 'Frigate camera server Thing ID' should be the ID of the instantiated and running frigateSVRServer 'thing' supporting the camera. The 'Camera name' is the name of the camera as it appears in the Frigate config, or the Frigate UI.

## Channels

### `frigateSVR Server` 'Thing' Channels

| Channel        | Type   | Read/Write  | Description                                                           |
|----------------|--------|-------------|-----------------------------------------------------------------------|
| API version    | String | R/O         | Version of the Frigate API being used                                 |
| Frigate UI URL | String | R/O         | URL to the Frigate UI for this server (useful for openHAB UI widgets) |


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

**Note:** 'Current event' and 'Prior to event' channels are updated with fgCurrentEventType. This ensures consistency of information passed to event handlers - there should be no 'stale' information left in any of the 'Cur' or 'Prev' channels. Note also that some of these values may change to NULL if the value on the Frigate server side is NULL. Thus, rules that wish to interrogate multiple 'cur' or 'prev' channels should trigger on changes to 'fgCurrentEventType' as this channel is updated once all other event channels have been updated. 

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

# Building

Conventional openHAB wisdom is to fork the complete add-on repository and work from there - but Java is noisy and busy enough without duplicating yet more 'stuff' that I am not planning to work on. There are also issues then in making it clear on initial visit to this repository exactly what this repository contains. So I am managing here only the part I am actually developing. Thus: the build process is as follows:

- Change to an empty directory in which you want to build.
- Pull the openhab-addons repository from openHAB
- Pull this repository using 'git submodule' into the 'bundles' directory of the openhab-addons directory, keeping the org.openhab.binding.mqtt.frigatesvr directory name (I guess you could use 'git subtree' if you wished?).
- Open the openhab-addons/bundles/pom.xml file in an editor:
  - Firstly check the version number at the top of the file in the `<parent>` hierarchy - do not change this but copy it somwehere.
  - In the `<modules>` section, add `<module>org.openhab.binding.mqtt.frigatesvr</module>`
- Open the bundles/org.openhab.binding.mqtt.frigatesvr/pom.xml
  - Check the version in the `<parent>` hierarchy. If it is not the same as that noted earlier from the bundles/pom.xml file, then change it so that it is (it most likely will not be as openHAB develops). If the numbers are not the same you will get a build error. Save file if changed
- In the openhab-addons directory, start the build.

In Linux, the steps are:

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

