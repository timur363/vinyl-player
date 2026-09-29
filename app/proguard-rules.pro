# Vinyl Player — R8 rules
#
# Goal: get R8's speed-ups (optimized, smaller Compose / AndroidX / Media3
# bytecode) without the usual reflection breakage. So:
#   * no renaming at all (-dontobfuscate): Gson, Retrofit, Room, kotlinx
#     serialization, NewPipe and friends keep working by name;
#   * our own code and the in-repo modules are kept whole but may still be
#     optimized;
#   * libraries that load classes by name are kept whole.
# Libraries not listed here are shrunk and optimized using their own
# consumer rules, which is where most of the gain comes from.

-dontobfuscate
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable,RuntimeVisible*Annotations,AnnotationDefault

# ── App + in-repo modules ────────────────────────────────────────────────────
-keep,allowoptimization class com.alananasss.** { *; }
-keep,allowoptimization class com.zionhuang.** { *; }
-keep,allowoptimization class com.metrolist.** { *; }
-keep,allowoptimization class com.my.kizzy.** { *; }

# ── NewPipeExtractor + Rhino (JavaScript engine, reflective) ────────────────
-keep class org.schabi.newpipe.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn org.schabi.newpipe.**

# ── Gson / Retrofit / OkHttp ─────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── kotlinx.serialization / Ktor ─────────────────────────────────────────────
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep class kotlinx.serialization.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**

# ── Misc libraries used by name ──────────────────────────────────────────────
-keep class com.mpatric.mp3agic.** { *; }
-keep class com.mocharealm.** { *; }
-keep class com.mikepenz.aboutlibraries.** { *; }
-keep class com.airbnb.lottie.** { *; }

# ── Things referenced but never present on Android ──────────────────────────
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
-dontwarn jdk.dynalink.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.jspecify.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn edu.umd.cs.findbugs.annotations.**
