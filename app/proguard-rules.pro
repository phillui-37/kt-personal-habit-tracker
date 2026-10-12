# Release minification is on. Backup JSON and routine templates use
# kotlinx.serialization, so keep the generated serializers and the
# Serializable annotation. Field names in stored JSON stay stable.
-keepattributes *Annotation*, InnerClasses

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}

-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers class * {
    static <1> *;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# R8 full mode strips annotations unless the annotated class is kept.
-if @kotlinx.serialization.Serializable class **
-keep,allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class <1>

-if @kotlinx.serialization.internal.NamedCompanion class *
-keep,allowshrinking,allowoptimization,allowobfuscation,allowaccessmodification class <1>

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn okhttp3.**
-dontwarn okio.**

# Tink (androidx.security) references compile-only Error Prone annotations.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
