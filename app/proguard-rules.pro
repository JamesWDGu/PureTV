# Kadb uses reflection to load TLS providers and optional Conscrypt implementations.
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.lsposed.hiddenapibypass.**

# Keep shell/protocol model classes that are constructed reflectively by kotlinx libraries.
-keep class com.flyfishxu.kadb.** { *; }
