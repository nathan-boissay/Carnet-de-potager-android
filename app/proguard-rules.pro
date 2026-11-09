# Keep kotlinx.serialization models for Retrofit
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class corsica.universita.carnet.**$$serializer { *; }
-keepclassmembers class corsica.universita.carnet.** {
    *** Companion;
}
-keepclasseswithmembers class corsica.universita.carnet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

