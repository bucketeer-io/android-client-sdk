# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

-keep class io.bucketeer.sdk.android.internal.model.** { *; }
-keepclassmembers class io.bucketeer.sdk.android.internal.model.** { *; }
-keepclassmembers class io.bucketeer.sdk.android.internal.model.** { public <init>(...); }
