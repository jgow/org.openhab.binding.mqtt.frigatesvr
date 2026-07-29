# FrigateSVR binding - CHANGELOG

## Versions:

- Versions prior to 1.5:
  - do not have video support
  - do have full event support
- Version 1.5:
  - introduced video streaming for birdseye and cameras (MJPEG only)
- Version 1.6:
  - has a rewritten video subsystem
  - adds an API forwarder
  - adds full multi-type streaming support
- Version 1.7
  - adds a channel on cameras containing the latest full JSON event string from Frigate, unmodified, filtered by camera (fgEventJSON)
- Version 1.8
  - Numerous fixes to stream handling, added Camera ThingActions to support a wider range of the Frigate API. Added channels to Camera Things to allow direct communication with Frigate API in terms of ThingActions. More ThingActions will be added in later releases.
- Version 1.9
  - Bugfix release:
    - snapshot update bug in v1.8 is fixed.
- Version 2.0
  - Bugfix release:
    - channel typos fixed; JSON event string now available
    - documentation update
    - error in URL strings corrected.
- Version 2.1
  - Bugfix release:
    - channels carrying camera stats such as FPS should now update properly.
- Version 2.2
  - Channel updates
  - Documentation updates.
- Version 3.0
  - BREAKING CHANGES: This version introduces a completely new architecture for camera Things and server Things that is much 'cleaner' and should improve performance. New channels are available, and new ThingActions have been added.
    - Upgrading to 3.x versions will almost certainly require you to re-create your Things.
    - New architecture for inter-Thing communication - the server Thing now acts as a bridge, sitting on top of MQTT. Cameras are children of the server Bridge. Basic operation should be unchanged.
    - New channels
      - Server Thing
        - fgTrackedObjects: list of tracked objects
      - Camera Thing
        - fgActionLastFrame: channel carrying result of ThingAction GetLastFrame
        - fgActionEventThumbnail: channel carrying result of ThingAction GetThumbnail
        - fgObjCount: Returns a count of total objects being tracked, by type. This makes using the camera as a sensor to turn on security lights much more straightforward.
        - fgObjCountActive: Returns a count of active objects, by type. This can aid integration into a security system
    - New Camera ThingActions
      - PTZ: allows camera PTZ to be controlled via a ThingAction
    - ThingAction cleanups
      - ThingActions are no longer asynchronous.
      - GetLastFrame and TriggerEvent have been clarified
  