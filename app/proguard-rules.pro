# Xposed/Vector instantiates the entry point reflectively. Keeping only the class
# does not preserve its no-argument constructor with current R8 versions.
-keep class org.matrix.chromext.MainHook {
    public <init>();
}
-keepattributes SourceFile,LineNumberTable
