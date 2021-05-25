TDLight Java
====================

[![Build Status](https://img.shields.io/github/workflow/status/tdlight-team/tdlight-java/Maven%20Package?style=flat-square)](https://travis-ci.com/tdlight-team/tdlight-java-natives)
[![Release tag](https://img.shields.io/github/v/release/tdlight-team/tdlight-java.svg?include_prereleases&style=flat-square)](https://github.com/tdlight-team/tdlight-java/releases)
[![Java profiler](https://local.cavallium.it/mirrors/jprofiler-logo/jprofiler-logo-badge.svg)](https://www.ej-technologies.com/products/jprofiler/overview.html)

A barebone java wrapper for TDLib (and TDLight)

This wrapper gives you direct access to TDLib API in Java.

## Requirements
Java versions: Java 8, 9, 10, 11, 12, 13, 14, 15, 16

Operating system: Linux, Windows, MacOS

Supported CPU architectures:
  - x86 (Linux, Windows)
  - amd64/x86_64 (Linux, Windows, MacOS)
  - armv6 (Linux)
  - armv7 (Linux)
  - aarch64/armv8 (Linux)

Required libraries for linux: OpenSSL and zlib

## Including TDLight Java in a project

There are two packages of TDLight:
  - [TDLight](#For-TDLight-Java-with-optimized-TDLight), with our optimized fork of TDLib (Reccomended for bots)
  - [TDLight with official TDLib](#For-TDLight-Java-with-official-TDLib) (Reccomended for GUI clients)

The two packages are compatible, but while TDLight is focused on long term resources usage and speed, TDLib is more focused on stability.

Choose one of the two different TDLight packages and then follow the guide below.

Replace `REPLACE_WITH_LATEST_VERSION` with the latest version of tdlight, you can find it on the **Releases** tab on github.
### For TDLight Java with optimized TDLight
#### Maven
Repository:
```xml
<repositories>
  <repository>
    <id>mchv</id>
    <name>MCHV Apache Maven Packages</name>
    <url>https://mvn.mchv.eu/repository/mchv/</url>
  </repository>
</repositories>
```
Dependency:
```xml
<dependencies>
  <dependency>
    <groupId>it.tdlight</groupId>
    <artifactId>tdlight-java</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
  </dependency>
  <dependency>
    <groupId>it.tdlight</groupId>
    <artifactId>tdlight-natives-linux-amd64</artifactId>
    <version>REPLACE_WITH_LATEST_NATIVES_VERSION</version>
  </dependency>
  <!-- include other native versions that you want, for example for macos, windows, and other architectures here -->
</dependencies>
```
#### Gradle
```groovy
repositories {
    maven { url "https://mvn.mchv.eu/repository/mchv/" }
}
dependencies {
    implementation 'it.tdlight:tdlight-java:REPLACE_WITH_LATEST_VERSION'
    implementation 'it.tdlight:tdlight-natives-linux-amd64:REPLACE_WITH_LATEST_NATIVES_VERSION'
    // include other native versions that you want, for example for macos, windows, and other architectures here
}
```
#### Natives inclusion
To use TDLight java for a specific platform, you need to include the related native dependencies:
- `tdlight-natives-linux-amd64`
- `tdlight-natives-linux-aarch64`
- `tdlight-natives-linux-x86`
- `tdlight-natives-linux-armv6`
- `tdlight-natives-linux-armv7`
- `tdlight-natives-linux-ppc64le`
- `tdlight-natives-windows-amd64`
- `tdlight-natives-osx-amd64`

### For TDLight Java with official TDLib
#### Maven
Repository:
```xml
<repositories>
  <repository>
    <id>mchv</id>
    <name>MCHV Apache Maven Packages</name>
    <url>https://mvn.mchv.eu/repository/mchv/</url>
  </repository>
</repositories>
```
Dependency:
```xml
<dependencies>
  <dependency>
    <groupId>it.tdlight</groupId>
    <artifactId>tdlib-java</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
  </dependency>
  <dependency>
    <groupId>it.tdlight</groupId>
    <artifactId>tdlib-natives-linux-amd64</artifactId>
    <version>REPLACE_WITH_LATEST_NATIVES_VERSION</version>
  </dependency>
  <!-- include other native versions that you want, for example for macos, windows, and other architectures here -->
</dependencies>
```
#### Gradle
```groovy
repositories {
    maven { url "https://mvn.mchv.eu/repository/mchv/" }
}
dependencies {
    implementation 'it.tdlight:tdlib-java:REPLACE_WITH_LATEST_VERSION'
    implementation 'it.tdlight:tdlib-natives-linux-amd64:REPLACE_WITH_LATEST_NATIVES_VERSION'
    // include other native versions that you want, for example for macos, windows, and other architectures here
}
```
#### Natives inclusion
To use TDLight java for a specific platform, you need to include the related native dependencies:
- `tdlib-natives-linux-amd64`
- `tdlib-natives-linux-aarch64`
- `tdlib-natives-linux-x86`
- `tdlib-natives-linux-armv6`
- `tdlib-natives-linux-armv7`
- `tdlib-natives-linux-ppc64le`
- `tdlib-natives-windows-amd64`
- `tdlib-natives-osx-amd64`

## Usage
Simple initialization of a native TDLib client
```java
package it.tdlight.example;

import it.tdlight.common.TelegramClient;
import it.tdlight.tdlight.ClientManager;
import it.tdlight.common.Init;

import it.tdlight.jni.TdApi;

public class Example {
    public static void main(String[] args) {
        // Initialize TDLight native libraries
        Init.start();

        // Create a client
        TelegramClient client = ClientManager.create();

        // Initialize the client
        client.initialize(Example::onUpdate, Example::onUpdateError, Example::onError);

        // Here you can use the client.

        // Documentation of tdlib methods can be found here:
        // https://tdlight-team.github.io/tdlight-docs
      
        // An example on how to use tdlight java can be found here:
        // https://github.com/tdlight-team/tdlight-java/blob/master/example/src/main/java/it.tdlight.example/Example.java

        // A similar example on how to use tdlib can be found here:
        // https://github.com/tdlib/td/blob/master/example/java/org/drinkless/tdlib/example/Example.java
    }

    private static void onUpdate(TdApi.Object object) {
        TdApi.Update update = (TdApi.Update) object;
        System.out.println("Received update: " + update);
    }

    private static void onUpdateError(Throwable exception) {
        System.out.println("Received an error from updates:");
        exception.printStackTrace();
    }

    private static void onError(Throwable exception) {
        System.out.println("Received an error:");
        exception.printStackTrace();
    }
}
```

### TDLib methods documentation
[TdApi class javadoc](https://tdlight-team.github.io/tdlight-docs)

### TDLight extended features
TDLight Java with TDLight has some extended features, that you can see on the [TDLight official repository](https://github.com/tdlight-team/tdlight).

This features are present only if you use the optimized TDLight package.

## About
### License
TDLight is licensed by Andrea Cavalli <andrea@cavallium.it> under the terms of the GNU Lesser General Public License 3

### Libraries licenses

JTDlib is licensed by Ernesto Castellotti <erny.castell@gmail.com> under the terms of the GNU Lesser General Public License 3

TDLib is licensed by Aliaksei Levin <levlam@telegram.org> and Arseny Smirnov <arseny30@gmail.com> under the terms of the Boost Software License				

OpenSSL is licensed under the terms of Apache License v2

Zlib is licensed under the terms of Zlib license
