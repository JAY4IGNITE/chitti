# Chitti release keep rules (minify is currently disabled; these make a future -minify build safe).

# MediaPipe GenAI / LiteRT use JNI and reflection on their own classes.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Room entities/DAOs are accessed by generated code; keep field names for @Entity classes.
-keep class com.owlcoders.chitti.db.entities.** { *; }
-keep class com.owlcoders.chitti.db.CapturedEvent { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Services/receivers referenced from the manifest
-keep class com.owlcoders.chitti.services.** extends android.app.Service { *; }
-keep class com.owlcoders.chitti.automation.ReminderReceiver { *; }
-keep class com.owlcoders.chitti.automation.BootReceiver { *; }
-keep class com.owlcoders.chitti.security.LinkGuardActivity { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
