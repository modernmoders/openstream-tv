# R8 rules for the release build. Hilt, Room, Media3, Coil, and OkHttp all
# ship consumer rules in their AARs — only kotlinx.serialization needs help:
# its generated serializer companions are looked up reflectively for our
# addon-protocol DTOs.
-keepclassmembers class dev.openstream.tv.** {
    *** Companion;
}
-keepclasseswithmembers class dev.openstream.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.openstream.tv.**$$serializer { *; }

# OkHttp's optional conscrypt/bouncycastle probes are absent by design.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
