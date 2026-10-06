# The release build is shrunk by R8 (unused code and resources leave the APK) but NEVER renamed: crash reports keep
# real names, and anything found by name at run time is unchanged. The release launch gate (scripts/launch-gate.py)
# proves it on an emulator before signing: the app opens, plays a stream, and plays an AV1 file through the FFmpeg
# decoder (nextlib), whose native code calls back into Java by name.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# P2P streams: libtorrent4j's SWIG bridge — native methods registered by name, and the native side calls the Java
# directors (alerts, callbacks) by name. Kept whole.
-keep class org.libtorrent4j.** { *; }
-keep interface org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# Software video and audio decoding (NextPlayer's nextlib, FFmpeg + dav1d): native methods, and native code that calls
# its decoders back by name. Kept whole.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keep interface io.github.anilbeesetti.nextlib.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**

# Media3's decoder buffers: FFmpeg's native code fills them through methods only it calls (grow, initForYuvFrame…),
# which R8 would otherwise drop as unused.
-keep class androidx.media3.decoder.** { *; }

# Any class with native methods keeps them and their names.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# Annotations and optional platforms the libraries reference but never need at run time.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn org.checkerframework.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
