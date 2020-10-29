package it.tdlight.common.utils;
public final class LibraryVersion {
    public static final String VERSION = "${project.version}";
    public static final String IMPLEMENTATION_NAME = "tdlib";
    public static final String LINUX_AMD64_CLASS = "it.tdlight.jni.tdlib.linux.amd64.LoadLibrary";
    public static final String LINUX_AARCH64_CLASS = "it.tdlight.jni.tdlib.linux.aarch64.LoadLibrary";
    public static final String WINDOWS_AMD64_CLASS = "it.tdlight.jni.tdlib.win.amd64.LoadLibrary";
    public static final String OSX_AMD64_CLASS = "it.tdlight.jni.tdlib.osx.amd64.LoadLibrary";
}