# OpenHAB Frigate SVR Binding : Camera ThingActions

The binding supports ThingActions giving access to the events model in Frigate.

## A note

Camera ThingActions are *asynchronous*. Calls to the ThingAction will return immediately, with an error code and message (see 'Structure' below). This error code and message, if showing success, indicates that the message has successfully been queued. An indeterminate time later, defined largely by the response times of the server Thing and the Frigate server itself, the result will appear in two camera channels (again, this is described in the 'Structure' section. The reason for this is essentially down to the design of OpenHAB. See the 'Notes' section for more detail.

## Structure

Camera ThingActions follow the standard pattern for ThingActions. Examples are provided in this document. Zero or more arguments should be passed to the ThingAction. It will return a map containing two elements:


| member          | type    |                                             |
|-----------------|---------|---------------------------------------------|
|response.rc      | boolean | this is the return status, true for success |
|response.message | string  | status message                              |

Success or failure at this point will indicate whether the call was successfully queued to the server, _not_ that it was successfully processed by Frigate.

Once processed by Frigate, the response will appear, at an indeterminate time later in one or both of the following Camera Thing channels:

| channel               |                       |
|-----------------------|-----------------------|
| fgLastProcessedFrame  | If the Frigate API call returns an image (e.g a snapshot), it will appear here. If the call does not return an image, this channel will not be updated |
| fgCamActionResult     | A JSON string consisting of two objects - see below |

The channel 'fgCamActionResult' contains a properly-formatted JSON object in string form. For a successful call, this channel will be updated to:

```
{
    "success" : true,
    "message" : <returned_data_from_Frigate> 
}
```

If the call fails, the channel will be updated to:
```
{
    "success" : false,
    "message" : <String_giving_details_of_error>
}
```
The exact format of the <returned_data_from_Frigate> will depend on the call, and this can be found from the Frigate API documentation

## Reference

1. **TriggerEvent**.
    - Arguments:
        - String eventLabel:  (label of the event)
        - String eventRequest: (optional JSON formatted string containing Frigate event request as per Frigate API documentation)
    - Returns:
        - fgTriggerEventResult message: 
            - success: a JSON string as described in the Frigate API documentation
            - failure: a string containing the reason for failure. If the Frigate API does not support this call, it will fail and a 404 error code will be returned.
        - fgLastProcessedFrame: not used or updated by this call.
    - Description:
        - For versions of Frigate >= 0.13.0-beta6, this ThingAction will trigger an event. The event will be created with the label 'eventLabel' and the argument 'eventRequest' should contain the JSON string expected by Frigate. It will fail with a 404 error for any earlier version of Frigate
        
2. **GetRecordingSummary**.
    - Arguments:
        - none.
    - Returns:
        - fgTriggerEventResult message: 
            - success: a JSON string containing the summary of recordings for this camera as described in the Frigate API
            - failure: a string containing the reason for failure - usually an HTTP error code
        - fgLastProcessedFrame: not used or updated by this call.
    - Description:
        - Returns a summary of recordings for this camera, as a JSON array.

        
## Using camera ThingActions in a rule - an example

```
var camActions = actions.thingActions("frigateCamera","mqtt:frigateCamera:122343:locationCam");

if (camActions != null) {
  var response = camActions.TriggerEvent("thisEvent","{}");
  console.log("response:rc="+response.rc);
  console.log("response:payload:"+response.desc);
} else {
  console.log("camera actions null");
}
```
The handling of the return from Frigate via fg

