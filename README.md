TDLight Java Wrapper
====================

## Requirements
JVM: Oracle JVM and OpenJDK

Java versions: Java 10, 11, 12, 13, 14

Operating system: Windows, Linux

Supported CPU architectures: amd64 (linux, windows), aarch64 (linux)

Required libraries for linux: OpenSSL and zlib

## Including TDLight in a project
Maven configuration example to use this version of tdlight-java:
```xml
<repositories>
  <repository>
    <id>tdlight-gihtub</id>
    <name>GitHub TDLight Team Apache Maven Packages</name>
    <url>https://maven.pkg.github.com/tdlight-team/tdlight-java</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>it.ernytech</groupId>
    <artifactId>tdlib</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
  </dependency>
</dependencies>
```
Gradle configuration example to use this version of tdlight-java:
```groovy
repositories {
     maven { url "https://maven.pkg.github.com/tdlight-team/tdlight-java" }
}
dependencies {
     implementation 'com.github.tdlight-team.tdlight-java:tdlib:REPLACE_WITH_LATEST_VERSION'
}
```

Replace `REPLACE_WITH_LATEST_VERSION` with the latest version of tdlight, you can find it on the **Releases** tab on github.

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

## About
#### Libraries and external code
Telegram Tdlib-TD Copyright owner and contributors: 
  - Aliaksei Levin <levlam@telegram.org>
  - Arseny Smirnov <arseny30@gmail.com>
  - hekkup
  - Vladislav Yashin <v.yashin.work@gmail.com>
  - cosmonawt
  - Aziz Kasymov 
  - Felix Krause <github@krausefx.com>
  - Mark
  
#### License
TDLight is licensed by Andrea Cavalli <andrea@cavallium.it>  under the terms of the GNU Lesser General Public License 3

JTDlib is licensed by Ernesto Castellotti under the terms of the GNU Lesser General Public License 3

TDLib is licensed by Aliaksei Levin Ale under the terms of the Boost Software License										   