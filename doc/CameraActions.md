# OpenHAB Frigate SVR Binding : Camera ThingActions

The binding supports ThingActions giving access to the events model in Frigate.

## A note

As of v3.x, Camera ThingActions are synchronous. Calls to the ThingAction will return immediately, with an error code and message (see 'Structure' below). This error code and message, if showing success, indicates that the message has successfully been queued. An indeterminate time later, defined largely by the response times of the server Thing and the Frigate server itself, the result will return directly. Where an image is returned, a successful call will result in a channel update. Text, or JSON objects, will be returned directly from the ThingAction.

Note this differs from prior versions - these used asynchronous calls.

## Structure

Camera ThingActions follow the standard pattern for ThingActions. Examples are provided in this document. Zero or more arguments should be passed to the ThingAction as listed. It will return a map containing two elements:
<br/>

| member          | type    |                                 |
|-----------------|---------|---------------------------------|
|response.rc      | boolean | return status, true for success |
|response.message | string  | status message                  |

<br/>
Success or failure at this point will indicate whether the call was successfully queued to the server, _not_ that it was successfully processed by Frigate.
Once processed by Frigate, the response will appear, at an indeterminate time later in one or both of the following Camera Thing channels from where they can be used in UI widgets:
<br/>

| channel               |                       |
|-----------------------|-----------------------|
| fgActionLastFrame  | Images returned from GetLastFrame |
| fgActionEventThumbnail | Images returned from GetThumbnail |

If the call fails, the channel will be updated to:

```javascript
{
    "success" : false,
    "message" : `<String_giving_details_of_error>`
}
```

## Using camera ThingActions in a rule - an example

Note that this does not show the processing of the output channels (fgTriggerEventResult, fgLastProcessedFrame), which would follow normal openHAB practice.

```javascript
var camActions = actions.thingActions("frigateCamera","mqtt:frigateCamera:122343:locationCam");

if (camActions != null) {
  var response = camActions.TriggerEvent("thisEvent","{}");
  console.log("response:rc="+response.rc);
  console.log("response:payload:"+response.message);
} else {
  console.log("camera actions null");
}
```

The above script will queue the request. Note that response.rc and response.message will determine success or failure of the queuing of the request: if rc is true the request has successfully been queued. This in and of itself does not mean that the request has been completed.

If the request is successfully queued, then an indeterminate time later one or both of the channels 'fgTriggerEventResult' and 'fgLastProcessedFrame' will be updated (and a separate rule can be triggered on changes to these channels, if required). The channel 'fgTriggerEventResult' will contain a text result from the call; these are described under the relevant API headers. The channel 'fgLastProcessedFrame' will be updated to an image, if the API call returns an image.

## Reference

**TriggerEvent(String eventLabel, String eventRequest)**.

- Description:

  For versions of Frigate >= 0.13.0-beta6, this ThingAction will trigger an event. The event will be created with the label 'eventLabel' and the argument 'eventRequest' should contain the JSON string expected by Frigate. It will fail with a 404 error for any earlier version of Frigate.

- Arguments: <br/>

  | argument     |                                                  |
  |--------------|--------------------------------------------------|
  | eventLabel   | desired label of the event. Must be alphanumeric |
  | eventRequest |optional JSON formatted string containing Frigate event request as per Frigate API documentation |

- Returns: <br/>

  | response.rc      | true if successful, false if failed |
  | response.message | 'ok' if successful, error message if failed |

**GetRecordingSummary()**.

- Description:

  Returns a summary of recordings for this camera, as a JSON array described in the Frigate API documentation

- Arguments:

  none

- Returns: <br/>

  | response.rc      | true if successful, false if failed |
  | response.message | 'ok' if successful, error message if failed |
  | response.result  | JSON string containing response from Frigate, blank on error |

**GetThumbnail(String eventLabel)**.

- Description:

  Returns the thumbnail for the given event label, or the latest event if 'any' is specified

- Arguments:

  | argument     |                                                  |
  |--------------|--------------------------------------------------|
  | eventLabel   | a string containing the label for which the thumbnail is required; use 'any' to retrieve the thumbnail for the latest |

- Returns: <br/>

  | response.rc      | true if successful, false if failed |
  | response.message | 'ok' if successful, error message if failed |

  | channel | failure | success |
  |----------------------|-----------------------|-------------|
  | fgActionEventThumbnail |no change|if the call is successful the thumbnail will be returned here|

**GetLastFrame(String eventParams)**.

- Description:

  Returns the most recent frame that Frigate has finished processing.

- Arguments: <br/>

  none

- Returns: <br/>

  | response.rc      | true if successful, false if failed |
  | response.message | 'ok' if successful, error message if failed |

  | channel | failure | success |
  |----------------------|-----------------------|-------------|
  | fgActionLastFrame |no change|if the call is successful the thumbnail will be returned here|
