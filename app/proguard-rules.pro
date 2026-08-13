# Keep JNI façade classes (loaded reflectively from lib).
-keepclasseswithmembernames class * {
    native <methods>;
}
