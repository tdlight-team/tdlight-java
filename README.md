TDLight Java
====================

A barebone java wrapper for TDLib (and TDLight)

This wrapper gives you direct access to TDLib API in Java.

## Requirements
JVM: Oracle JVM or OpenJDK

Java versions: Java 8, 9, 10, 11, 12, 13, 14, 15

Operating system: Windows, Linux

Supported CPU architectures: amd64 (linux, windows), aarch64 (linux)

Required libraries for linux: OpenSSL and zlib

## Including TDLight Java in a project

There are two packages of TDLight:
  - [TDLight](#For-TDLight-Java-with-optimized-TDLight), with our optimized fork of TDLib (Reccomended for bots) [![Build Status](https://travis-ci.org/tdlight-team/tdlight-java.svg?branch=dev)](https://travis-ci.org/tdlight-team/tdlight-java)
  - [TDLight with official TDLib](#For-TDLight-Java-with-official-TDLib) (Reccomended for GUI clients) [![Build Status](https://travis-ci.org/tdlight-team/tdlight-java.svg?branch=td-dev)](https://travis-ci.org/tdlight-team/tdlight-java)

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
<dependency>
<groupId>it.tdlight</groupId>
<artifactId>tdlight-java</artifactId>
<version>REPLACE_WITH_LATEST_VERSION</version>
</dependency>
```
#### Gradle
```groovy
repositories {
     maven { url "https://mvn.mchv.eu/repository/mchv/" }
}
dependencies {
     implementation 'it.tdlight:tdlight-java:REPLACE_WITH_LATEST_VERSION'
}
```
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
<dependency>
<groupId>it.tdlight</groupId>
<artifactId>tdlib-java</artifactId>
<version>REPLACE_WITH_LATEST_VERSION</version>
</dependency>
```
#### Gradle
```groovy
repositories {
     maven { url "https://mvn.mchv.eu/repository/mchv/" }
}
dependencies {
     implementation 'it.tdlight:tdlib-java:REPLACE_WITH_LATEST_VERSION'
}
```

## Usage
Simple initialization of a native TDLib client
```java
import java.io.File;

import it.tdlight.tdlight.Client;
import it.tdlight.tdlight.Init;
import it.tdlight.tdlight.Log;

public class Example {
    public static void main(String[] args) {
        // Initialize TDLight native libraries
        Init.start();

        // Set TDLib log level to 1
        Log.setVerbosityLevel(1);

        // Uncomment this line to print TDLib logs to a file
        // Log.setFilePath("logs" + File.separatorChar + "tdlib.log");
        
        Client client = new Client();
        
        // Initialize the TDLib client
        client.initializeClient();

        // Now you can use the client
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
