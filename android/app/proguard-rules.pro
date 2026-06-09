# 压缩混淆规则
-keepattributes *Annotation*

# 保留 kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.remoteconfig.override.model.**$$serializer { *; }

# 保留 libsu
-keep class com.topjohnwu.superuser.** { *; }
