# libsu uses reflection for root binaries
-keep class com.github.topjohnwu.libsu.** { *; }
-dontwarn com.github.topjohnwu.libsu.**

# NanoHTTPD is plain, but keep its service entry points
-keep class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**

# jBCrypt
-keep class org.mindrot.jbcrypt.** { *; }
-dontwarn org.mindrot.jbcrypt.**

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase { <init>(); }
