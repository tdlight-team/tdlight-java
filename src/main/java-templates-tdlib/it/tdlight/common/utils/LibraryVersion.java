package it.tdlight.common.utils;
public final class LibraryVersion {
    public static final String VERSION = "${project.version}";
    public static final String IMPLEMENTATION_NAME = "tdlib";
    public static final Class<?> LINUX_AMD64_CLASS = it.tdlight.jni.tdlib.linux.amd64.LoadLibrary.class;
    public static final Class<?> LINUX_AARCH64_CLASS = it.tdlight.jni.tdlib.linux.aarch64.LoadLibrary.class;
    public static final Class<?> WINDOWS_AMD64_CLASS = it.tdlight.jni.tdlib.win.amd64.LoadLibrary.class;
    public static final Class<?> OSX_AMD64_CLASS = it.tdlight.jni.tdlib.osx.amd64.LoadLibrary.class;
}