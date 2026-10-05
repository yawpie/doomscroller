# MediaPipe's protobuf-lite schemas read generated fields by name through reflection.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ponytail: retain Flogger for stack lookup; narrow keep rules if its APK size matters.
-keep class com.google.common.flogger.** { *; }

# JNI reads Java data-holder fields and callbacks by their original names.
-keep class com.google.mediapipe.framework.** { *; }

# Optional graph profiling/template APIs are absent from tasks-vision and unused here.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
