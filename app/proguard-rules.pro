-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Keep-правила для PDFBox-Android, чтобы избежать проблем при минификации
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.tom_roush.pdfbox.**

-keep class org.apache.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.apache.commons.logging.**
-dontwarn org.bouncycastle.**

# Правила для Google ML Kit Document Scanner
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# Правила для User Messaging Platform (UMP) Consent SDK
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    private java.lang.Object a;
}
-keep class kotlinx.coroutines.CompletedExceptionally { *; }
-keep class kotlinx.coroutines.JobCancellationException { *; }
-keep class kotlinx.coroutines.flow.internal.AbortFlowException { *; }