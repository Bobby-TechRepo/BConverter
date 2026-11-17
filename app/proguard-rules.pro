# R8 rules for release builds. Hilt, Room, WorkManager and Media3 ship their own consumer rules; these cover what they do not.

# FFmpegKit: the native libraries call back into these classes by name (JNI), so they must not be renamed or removed.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.arthenica.**

# Stack traces from crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
