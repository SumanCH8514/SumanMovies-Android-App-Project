-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class com.sumanonline.sumanmovies.data.local.** { *; }
-keep class com.sumanonline.sumanmovies.data.remote.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
