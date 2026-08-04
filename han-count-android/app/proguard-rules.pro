# Han Count Android — keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebView / JS bridge surface (no custom @JavascriptInterface in v1, keep defaults safe)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
