# FamiChat R8 / ProGuard rules
# Shrinks unused code (e.g., unused Material Icons Extended classes & library code)
# while preserving class/method names and all JNI/reflection entry points for maximum safety.

-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# --- WebRTC (JNI native callbacks from libjingle_peerconnection_so.so) ---
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# --- App data models, mappers, calls, and manifest components ---
-keep class com.example.data.model.** { *; }
-keep class com.example.calls.** { *; }
-keep class com.example.push.** { *; }
-keep class com.example.MainActivity { *; }

# --- Firebase / Google Play Services (rely on official AAR consumer rules + registrars) ---
-keep class * implements com.google.firebase.components.ComponentRegistrar
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Jetpack Credentials & Google Identity ---
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# --- WorkManager ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.InputMerger
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- OkHttp / Conscrypt / optional JVM annotations referenced by transitive libs ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.**
