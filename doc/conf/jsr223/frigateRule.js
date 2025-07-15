'use strict';

var OPENHAB_CONF = java.lang.System.getenv("OPENHAB_CONF");
var FileReader = Java.type("java.io.FileReader");
var FileWriter = Java.type("java.io.FileWriter");
var BufferedReader = Java.type("java.io.BufferedReader");
var BufferedWriter = Java.type("java.io.BufferedWriter");
var Files = Java.type("java.nio.file.Files");
var Paths = Java.type("java.nio.file.Paths");
var StandardCopyOption = Java.type("java.nio.file.StandardCopyOption");
var ZonedDateTime = Java.type("java.time.ZonedDateTime");
var DateTimeFormatter = Java.type("java.time.format.DateTimeFormatter");
var JsonParser = Java.type("com.google.gson.JsonParser");

var GsonBuilder = Java.type("com.google.gson.GsonBuilder");
var gson = new GsonBuilder().setPrettyPrinting().create();

/*
OpenHAB in this case has access to the same volume mount as Frigate writes its data to.

When an event ends, this rule 
- writes alert level events into a JSON data store in the openHAB html mount
- copies the frigate jpg from the frigate mounted volume to the openHAB html folder

This allows an OpenHAB sitemap to reference an HTML summary of recent frigate alerts

Trying to build this in OpenHAB rules DSL is cumbersome, javascript is a better fit for JSON manipulation
*/

rules.JSRule({
  name: "Frigate Jsr233 Event End",
  description: "Logs end event from fgDrivewayEventType to a JSON file",
  triggers: [triggers.ItemStateChangeTrigger("fgDrivewayEventType")],
  execute: function(module, input) {

    var newState = items.getItem("fgDrivewayEventType").state;

    if (newState == "end") {

      // collect current state
      var now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      var eventId = items.getItem("fgDrivewayEventID").state;
      var zones = items.getItem("fgDrivewayCurEnteredZone").state;
      var label = items.getItem("fgDrivewayCurLabel").state;
      var severityJson = items.getItem("fgDrivewayCurMaxSeverity").state;
      var hasSnapshot = items.getItem("fgDrivewayCurHasSnapshot").state;
      var severity = JSON.parse(severityJson);

      if (severity != "alert")
      {
        console.log("ignoring event as it isn't severity==alert");
        return;
      }

      if (!hasSnapshot)
      {
        console.log(`eventId=${eventId}, label=${label}, severity=${severity}: No snapshot so exiting`);
        return;
      }
      
      // read our json file repository
      var filePath = OPENHAB_CONF + "/html/data/frigate-events.json";
      var eventsArray;

      try {
        var reader = new BufferedReader(new FileReader(filePath));
        var jsonContent = "";
        var line;
        while ((line = reader.readLine()) !== null) {
          jsonContent += line;
        }
        reader.close();

        eventsArray = JsonParser.parseString(jsonContent).getAsJsonArray();
      } catch (e) {
        // If file doesn't exist or can't be read, start new array
        eventsArray = new java.util.ArrayList();
      }

      // Copy the clip jpg to openhab static folder. Another way would be to serve the original content in ngnix route perhaps
      try {

        var sourcePath = Paths.get("/srv/camera/frigate/clips/driveway-" + eventId + ".jpg");
        var destPath = Paths.get(OPENHAB_CONF + "/html/camera/" + eventId + ".jpg");

        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        console.log("Copied " + sourcePath + " to " + destPath);

      } catch (e) {
        console.error("Failed to copy file: " + e.message);
      }

      // This isn't very useful as it will populate the item asynchronously and we would have to track the event we triggered it for
      // try {

      //   var cameraActions = actions.Things.getActions("frigateCamera", "mqtt:frigateCamera:PiMqttBroker:drivewayCamera");

      //   if (cameraActions !== null) {
      //       var thumbnailBytes = cameraActions.GetThumbnail(eventId);
      //       // Now you can do something with thumbnailBytes, like save it or log its length
      //       console.log("Got thumbnail with length: " + thumbnailBytes.length);
      //   } else {
      //       console.error("Frigate camera actions not available");
      //   }

      // } catch (e) {
      //   console.error("Failed to copy file: " + e.message);
      // }

      // create a new event object and write it to the json repository
      try {

        var zoneJson = JSON.parse(zones);

        // Have to convert this array to an ArrayList for gson.toJsonTree
        var ArrayList = Java.type("java.util.ArrayList");
        var zoneList = new ArrayList();

        for (var i = 0; i < zoneJson.length; i++) {
          zoneList.add(zoneJson[i]);
        }

        var HashMap = Java.type("java.util.HashMap");
        var newEvent = new HashMap();

        newEvent.put("time", now);
        newEvent.put("eventId", eventId);
        newEvent.put("label", JSON.parse(label));
        newEvent.put("zones", zoneList);
        newEvent.put("severity", severity);
      
        eventsArray.add(gson.toJsonTree(newEvent));

        var writer = new BufferedWriter(new FileWriter(filePath));
        writer.write(gson.toJson(eventsArray));
        writer.close();

        console.log("Frigate Event JSON written: " + filePath);

      } catch (e) {
        console.error("Frigate Event JSON failed: " + e);
      }
    }
  }
});