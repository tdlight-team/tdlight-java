package it.tdlight.common.utils;
public final class LibraryVersion {
    public static final String VERSION = "${project.version}";
    public static final String IMPLEMENTATION_NAME = "tdlight";
    public static final Class<?> LINUX_AMD64_CLASS = it.tdlight.jni.tdlight.linux.amd64.LoadLibrary.class;
    public static final Class<?> LINUX_AARCH64_CLASS = it.tdlight.jni.tdlight.linux.aarch64.LoadLibrary.class;
    public static final Class<?> WINDOWS_AMD64_CLASS = it.tdlight.jni.tdlight.win.amd64.LoadLibrary.class;
    public static final Class<?> OSX_AMD64_CLASS = it.tdlight.jni.tdlight.osx.amd64.LoadLibrary.class;
}