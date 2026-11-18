-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class com.sumanonline.sumanmovies.data.local.** { *; }
-keep class com.sumanonline.sumanmovies.data.remote.** { *; }
