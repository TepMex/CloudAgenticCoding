-keepclassmembers class * extends org.osmdroid.views.overlay.Overlay { *; }

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.tepmex.zoulushang.**$$serializer { *; }
-keepclassmembers class com.tepmex.zoulushang.** {
    *** Companion;
}
-keepclasseswithmembers class com.tepmex.zoulushang.** {
    kotlinx.serialization.KSerializer serializer(...);
}
