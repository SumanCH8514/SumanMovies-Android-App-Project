# SumanMovies Android resources

## Files
- `app/src/main/res/mipmap-mdpi` through `mipmap-xxxhdpi`: legacy launcher icon PNGs.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: adaptive icon declaration.
- `app/src/main/res/drawable/ic_launcher_foreground.png`: adaptive foreground layer.
- `app/src/main/res/drawable/ic_launcher_background.png`: adaptive background layer.
- `app/src/main/res/drawable/sumanmovies_splash.png`: 1080x1920 splash artwork.
- `icon_512.png`: 512x512 combined launcher/store icon.
- `adaptive_foreground_1024.png`, `adaptive_background_1024.png`: full-size source layers.
- `splash_1080x1920.png`: full-size splash export.

## AndroidManifest.xml
Set the application icon:
```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher"
    ... >
</application>
```

## Adaptive icon
The `mipmap-anydpi-v26/ic_launcher.xml` resource is already included. Android 8.0+ launchers apply their own icon mask.

## Splash screen
Use `@drawable/sumanmovies_splash` as artwork in your splash implementation. For Android 12+, use the AndroidX Core SplashScreen API and a simplified icon on the system splash screen; display the full artwork in your app's first screen if desired.

## Brand colors
- Navy: `#030C2A`
- Electric blue: `#087BFF`
- Cyan: `#00C8FF`
- Orange: `#FF8A00`
- Gold: `#FFD21A`

Note: launcher icons are intentionally text-free in the adaptive foreground for better legibility at small sizes. The splash artwork carries the full SumanMovies wordmark and tagline.
