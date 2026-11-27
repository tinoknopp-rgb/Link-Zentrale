# JNA + libsodium (Secrets verschlüsseln) arbeiten mit Reflection
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.goterl.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-dontwarn org.slf4j.**
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Tink (security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
