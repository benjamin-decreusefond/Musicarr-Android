# kotlinx.serialization — keep serializers for our models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.musicarr.android.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.musicarr.android.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
