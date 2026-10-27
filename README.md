# HIDEOUT Android Starter (v0.1)

A native Android starter for a free-to-host, real-world hide-and-seek game.

## What works in this prototype
- Create a local demo room with a six-character code
- Enter a code to simulate joining
- Choose Hunters or Runners
- Start and reset a 10-minute countdown
- Navigate between Lobby, Map Preview, and Chat
- Send local demo chat messages
- Demo ping button and shrinking-zone visual placeholder

## What is not connected yet
This is a UI prototype. It does **not** yet share room codes between devices, fetch GPS, render a real map, sync chat, or enforce shrinking zones on a server. The app declares foreground location permissions but does not request or use them yet.

## Open in Android Studio
1. Install Android Studio with a JDK 17-compatible setup.
2. Extract this ZIP.
3. In Android Studio choose **Open** and select the `HIDEOUT-Android-Starter` folder.
4. Let Gradle sync. If prompted, install Android SDK Platform 35.
5. Run on an Android emulator or physical device.

## Next implementation milestones
1. **Real rooms:** Firebase project + anonymous sign-in + Firestore room documents and security rules.
2. **Real maps:** Add a maintained Android map SDK (for example, Google Maps or a suitable OpenStreetMap library), then show the player's location only after runtime permission is granted.
3. **Live positions:** Publish coarse/limited location updates only during an active game, with explicit opt-in and clear stop controls.
4. **Zone shrinking:** Store the zone center, initial radius, start time, and end radius in the room; derive the radius from server time so all players see the same zone.
5. **Pings:** Rate-limit requests and return only the designed hint, rather than exposing a runner's exact location by default.
6. **Team chat:** Use room-scoped messages with server-side checks so players can only access their team's chat.
7. **Safety and reliability:** Host-only controls, invite-code expiry, report/leave controls, anti-cheat checks, and battery-conscious location intervals.

## Free hosting approach
Use a free-tier backend while prototyping, and monitor quotas. No hosting fee should be charged to create a room in the app. Third-party cloud usage can still exceed free quotas if the app grows, so set budget alerts and restrict database rules before inviting real users.

## Privacy and physical safety
- Request location permission only when the player enters a game.
- Do not collect background location in the first version.
- Explain who can see location and when tracking ends.
- Use a bounded public play area, avoid roads/private property, and allow players to quit immediately.
- Do not use this prototype for a real game until live backend rules and location privacy have been tested.
