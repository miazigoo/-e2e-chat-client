# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Signal protocol classes can fail in release when constructors or native-facing
# types are renamed/optimized away. Keep the full Whispersystems surface intact.
-keep class org.whispersystems.** { *; }
-dontwarn org.whispersystems.**

# Retrofit needs generic signatures and HTTP annotations at runtime to resolve
# suspend return types like ApiEnvelopeDto<T>. Without these release builds can
# degrade response types to Object and fail converter creation.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn retrofit2.Platform
-dontwarn kotlin.Unit
