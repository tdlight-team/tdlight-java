package it.tdlight.common.utils;
public final class LibraryVersion {
    public static final String VERSION = "${project.version}";
    public static final String IMPLEMENTATION_NAME = "tdlight";
    public static final String LINUX_AMD64_CLASS = "it.tdlight.jni.tdlight.linux.amd64.LoadLibrary";
    public static final String LINUX_AARCH64_CLASS = "it.tdlight.jni.tdlight.linux.aarch64.LoadLibrary";
    public static final String WINDOWS_AMD64_CLASS = "it.tdlight.jni.tdlight.win.amd64.LoadLibrary";
    public static final String OSX_AMD64_CLASS = "it.tdlight.jni.tdlight.osx.amd64.LoadLibrary";
}