# Castor ProGuard Rules

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Keep Room entities
-keep class com.castor.core.data.db.entity.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.castor.**$$serializer { *; }
-keepclassmembers class com.castor.** {
    *** Companion;
}
-keepclasseswithmembers class com.castor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.* { *; }

# Keep llama.cpp JNI methods
-keep class com.castor.core.inference.llama.LlamaCppEngine {
    native <methods>;
}

# Keep model classes used in serialization
-keep class com.castor.core.common.model.** { *; }
