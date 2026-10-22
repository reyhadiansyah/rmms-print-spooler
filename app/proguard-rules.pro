-dontobfuscate
-dontoptimize
-repackageclasses
-allowaccessmodification

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable

-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-keep class com.rmms.printspooler.** { *; }
-keep class com.google.gson.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.datastore.** { *; }