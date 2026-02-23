# Add project specific ProGuard rules here.
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep HandDetector public API
-keep class com.smartcheck.sdk.HandDetector { *; }
-keep class com.smartcheck.sdk.DetectionResult { *; }
