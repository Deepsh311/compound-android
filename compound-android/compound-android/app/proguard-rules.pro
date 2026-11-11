# Keep kotlinx.serialization metadata if you turn on minification later.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.compound.data.** { *** Companion; }
-keepclasseswithmembers class app.compound.data.** { kotlinx.serialization.KSerializer serializer(...); }
