-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Maps
-keep class com.google.android.libraries.** { *; }
-dontwarn com.google.android.libraries.**

# AndroidX Security / Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# App models serialized via JSONObject reflection-free; nothing to keep.
