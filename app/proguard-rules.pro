-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Maps + Places + Utils
-keep class com.google.android.libraries.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.libraries.**
-dontwarn com.google.maps.android.**

# Directions JSON models (reflection-free)
-keepclassmembers class com.cybersec.smartroute.util.DirectionsClient$* { *; }

# AndroidX Security / Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# App models serialized via JSONObject reflection-free; nothing to keep.
