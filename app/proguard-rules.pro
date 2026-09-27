# LiteRT-LM reaches into these from native code and via kotlin-reflect.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { native <methods>; }
-dontwarn com.google.ai.edge.litertlm.**
