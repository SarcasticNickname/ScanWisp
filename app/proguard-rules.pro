# Add project specific ProGuard rules here.
# By default, the flags in this file are applied to all build types.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If you are using Google Play Services library to support devices running
# Android 2.3 (API level 9) and lower, the ProGuard configuration file for
# this library is located in the SDK installation directory.
#
# /tools/proguard/proguard-android-optimize.txt

# The following instruction shrinks and obfuscates the code.
# You can specify this file in the project.properties file.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# START: AI_MODIFIED_BLOCK
# Keep-правила для PDFBox-Android, чтобы избежать проблем при минификации
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.tom_roush.pdfbox.**

-keep class org.apache.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.apache.commons.logging.**
-dontwarn org.bouncycastle.**
