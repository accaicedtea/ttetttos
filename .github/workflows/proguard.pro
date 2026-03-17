-injars       target/demo-1.jar
-outjars      target/demo-1-obf.jar
-libraryjars  <java.home>/jmods
-libraryjars  target/lib(!META-INF/versions/*)

-keep public class com.example.App {
    public static void main(java.lang.String[]);
}
-keep public class com.example.**Controller { *; }
-keepclassmembers class com.example.model.** { *; }
-keepclassmembers class * { @javafx.fxml.FXML *; }

-adaptresourcefilenames
-adaptresourcefilecontents **.fxml,**.properties

-optimizationpasses 3
-allowaccessmodification
-repackageclasses ''

-keepattributes Exceptions,InnerClasses,Signature,*Annotation*

-dontwarn **
-dontnote **
