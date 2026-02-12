UI Layer Overview
This README focuses on the new UI layer I’ve built, covering the features around the library, cover art handling, and the general playback UX. It’s not meant to replace the main project documentation, but to give you a closer look at what’s happening under the hood of the Android app interface.

What’s Under the Hood (Tech Stack)
The app uses a hybrid approach: the core is still Java Activities, but the UI is now powered by Jetpack Compose hosted via ComposeView. I built the interface using Kotlin and Compose Material3 to keep it modern. For the heavy lifting—like fetching metadata and refreshing covers—we use Coroutines, while LiveData keeps the UI state in sync.

For images, Coil handles the loading, and we rely on OkHttp plus org.json to talk to TheGamesDB API. Persistent data, like your "last played" game and playtime tracking, lives in SharedPreferences, while cover art gets cached locally in filesDir/covers.

Configuration
The only external dependency you really need to worry about is the TheGamesDB API key. Make sure it’s configured where the app expects it (check the existing project config). Without a valid key, the app won't be able to pull down descriptions or new cover art, and it will fall back to placeholders or whatever it has in the cache.

API key location: app/build.gradle in the app module, in the `buildConfigField` for `TGDB_API_KEY`.

The User Interface
Library & Navigation
The library is now much more flexible. You can switch between Grid, List, and Carousel views, and everything is sorted alphabetically to keep things organized. The search bar is smarter, too—it filters based on both the raw title and a "normalized" version (cleaning up special characters). I also compacted the top bar height and icon sizing to fit more content on screen.

Carousel Layout
I put a lot of work into the carousel to make the layout feel natural.

In Landscape: The height adjusts dynamically so you never have to scroll vertically or see clipped content. Long titles wrap to multiple lines; the scrolling marquee only kicks in if the title is still too long. You'll also see the game description right under the title, with a handy "Resume" chip if you were playing that game recently.
In Portrait: The "Resume" card moves into the main scrollable area, and titles use the marquee animation when they overflow.
Cover Art Logic
I didn't want cover art looking stretched or cut off, so I used ContentScale.Fit. The system is smart about picking the right image—it filters by aspect ratio (with some tolerance) and picks the best match based on title similarity. If a cover is missing, it uses a placeholder icon. Everything gets cached locally, so it loads fast next time. You can also trigger a "Force Refresh" to wipe the cache and re-download everything, which also happens automatically after an install.

Titles & Metadata
To make the library look cleaner, titles are automatically "normalized." This strips out trademarks, brackets, locale tokens, version numbers, and other noise. It preserves special characters like "&" for display (e.g., "Ratchet & Clank") and fixes spacing between letters and numbers where needed.

Resuming & Playtime
The app now tracks how long you play. It stores session duration in SharedPreferences and displays it in the UI. There’s also a "Resume" feature: when you open the app, it remembers the last game you played and lets you jump right back in, either via a button in portrait or an inline chip in landscape.

Background
To give the app a bit of personality, I added a subtle wave animation in the background. It features the classic PlayStation shapes (triangle, circle, cross, square) floating along the wave. It runs on frame time to ensure the animation is smooth and continuous without resetting unexpectedly.

Where to Look in the Code
If you want to dive into the implementation, here are the key areas:

UI Screens: app/src/main/java/aenu/aps3e/ui/MainScreen.kt
Animation: app/src/main/java/aenu/aps3e/ui/WaveBackground.kt
Cover Logic: app/src/main/java/aenu/aps3e/data/CoverRepository.kt
Activity State: app/src/main/java/aenu/aps3e/MainActivity.java
Playtime Tracking: app/src/main/java/aenu/aps3e/EmulatorActivity.java