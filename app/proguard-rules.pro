# ONNX Runtime JNI orqali ishlaydi va sinf nomlariga tayanadi.
# R8 ularni qayta nomlasa yoki olib tashlasa, model ishga tushishda
# UnsatisfiedLinkError bilan qulaydi — bu faqat reliz build'da chiqadi,
# ya'ni debug'da sezilmaydi.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { native <methods>; }
-dontwarn ai.onnxruntime.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# DataStore
-keep class androidx.datastore.** { *; }

# Xizmat va qabul qiluvchilar manifestdan nom bo'yicha chaqiriladi
-keep class com.haramhide.app.ProtectionService { *; }
-keep class com.haramhide.app.BootReceiver { *; }
-keep class com.haramhide.app.MainActivity { *; }
-keep class com.haramhide.app.ProjectionRequestActivity { *; }
-keep class com.haramhide.app.TestPatternActivity { *; }

# Stack trace o'qilishi uchun
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
