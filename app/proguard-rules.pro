# GrihaLedger R8 / ProGuard Configuration

# Crashlytics readable deobfuscated stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep public class * extends java.lang.Exception

# Preserve @Serializable Navigation Compose routes and domain enums/models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.example.ui.navigation.** { *; }
-keepclassmembers class com.example.ui.navigation.** { *; }
-keepclassmembers,includedescriptorclasses class com.example.domain.model.** { *; }

# Keep kotlinx.serialization runtime serializers safely under R8 full mode
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }

# Keep AndroidX Credential Manager / Google Identity reflection classes if shrunk
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Optional Play / Apache / Google API client annotations referenced by transitive Firebase libraries
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
