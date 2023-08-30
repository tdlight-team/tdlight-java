<div style="text-align: center" align="center">
    <a href="https://github.com/tdlight-team/tdlight-java"><img src="./.media/tdlight-logo.png" alt="TDLight logo" style="width: 5rem; height: 5rem"></a>
    <h1>TDLight Java</h1>
    <p>Complete Bot and Userbot Telegram library written in Java, based on <a href="https://github.com/tdlib/td">TDLib</a></p>
    <a href="https://github.com/tdlight-team/tdlight-java/actions/workflows/maven-publish.yml">
<img alt="Maven package" src="https://github.com/tdlight-team/tdlight-java/actions/workflows/maven-publish.yml/badge.svg?branch=master"></a>
    <a href="https://github.com/tdlight-team/tdlight-java/releases">
        <img alt="Release" src="https://img.shields.io/github/v/release/tdlight-team/tdlight-java.svg?include_prereleases&style=flat-square">
    </a>
    <a href="https://www.ej-technologies.com/products/jprofiler/overview.html">
        <img alt="JProfiler" src="https://local.cavallium.it/mirrors/jprofiler-logo/jprofiler-logo-badge.svg">
    </a>
</div>
<br>

## 💻 Supported platforms

**Java versions**: from Java 17 to Java 19+ (Java 8 to 16 is supported if you use the following dependency classifier: `jdk8`)

**Operating systems**: Linux, Windows, MacOS

**CPU architectures**:

- amd64/x86_64 (Linux, Windows, MacOS)
- armhf/armv7 (Linux)
- aarch64/armv8/arm64 (Linux)
- ppc64el/ppc64le (Linux)

## 📚 Required libraries
- **Linux: libc++, OpenSSL1/OpenSSL3, zlib**
- **MacOS: OpenSSL**
- **Windows: [Microsoft Visual C++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe)**

### Install OpenSSL on macOS

You must install `openssl@3` using the <a href="https://brew.sh">brew package manager </a>, then link openssl
to `/usr/local/opt/openssl`

If you don't know how to do this, type the following commands in your terminal:

```bash
brew install openssl@3
ln -sf /usr/local/Cellar/openssl@3/3.0.0 /usr/local/opt/openssl
```

## 📚 How to use the library

### Setting up the library using Maven

If you are using Maven, edit your `pom.xml` file as below:

```xml

<project>
	<repositories>

		<!-- Add the following repository -->
		<repository>
			<id>mchv</id>
			<name>MCHV Apache Maven Packages</name>
			<url>https://mvn.mchv.eu/repository/mchv/</url>
		</repository>

	</repositories>

	<dependencyManagement>
		<dependencies>
			
			<!-- Add the following dependency -->
			<dependency>
				<groupId>it.tdlight</groupId>
				<artifactId>tdlight-java-bom</artifactId>
				<version>VERSION</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>

		<!-- Add the following dependencies -->
		<dependency>
			<groupId>it.tdlight</groupId>
			<artifactId>tdlight-java</artifactId>
			<!-- Java 8 is supported if you use the following dependency classifier: <classifier>jdk8</classifier> -->
			<!-- don't specify the version here -->
		</dependency>
		<!-- Example linux amd64 (GNU GCC) ssl1 natives -->
		<dependency>
			<groupId>it.tdlight</groupId>
			<artifactId>tdlight-natives</artifactId>
			<classifier>linux_amd64_gnu_ssl1</classifier>
			<!-- don't specify the version here -->
		</dependency>
		<!-- Example windows amd64 natives -->
		<dependency>
			<groupId>it.tdlight</groupId>
			<artifactId>tdlight-natives</artifactId>
			<classifier>windows_amd64</classifier>
			<!-- don't specify the version here -->
		</dependency>
		<!-- ... -->
		<!-- Include other native classifiers, for example linux_amd64_ssl3, macos_amd64, ... -->

	</dependencies>
</project>
```

Replace `VERSION` with the latest release version, you can find
it [here](https://github.com/tdlight-team/tdlight-java/releases).

## Setting up the library using Gradle

If you are using Gradle, add the following lines into your `build.gradle` file

```groovy
repositories {
	maven { url "https://mvn.mchv.eu/repository/mchv/" }
}
dependencies {
	// import the BOM
	implementation platform('it.tdlight:tdlight-java-bom:VERSION')

	// do not specify the versions on the dependencies below!
	implementation group: 'it.tdlight', name: 'tdlight-java' // Java 8 is supported if you use the following dependency classifier: `jdk8`
	implementation group: 'it.tdlight', name: 'tdlight-natives', classifier: 'linux_amd64_gnu_ssl1'
	// Include other native classifiers, for example linux_amd64_clang_ssl3, macos_amd64, ... -->
}
```

Replace `VERSION` with the latest release version, you can find
it [here](https://github.com/tdlight-team/tdlight-java/releases).

## ⚒ Native dependencies

To use TDLight Java you need to include the native libraries, by specifying one of the following classifier for each tdlight-natives dependency:

- `linux_amd64_clang_ssl3`
- `linux_amd64_gnu_ssl1`
- `linux_amd64_gnu_ssl3`
- `linux_arm64_clang_ssl3`
- `linux_arm64_gnu_ssl1`
- `linux_arm64_gnu_ssl3`
- `linux_armhf_gnu_ssl1`
- `linux_armhf_gnu_ssl3`
- `linux_ppc64el_gnu_ssl3`
- `linux_riscv64_gnu_ssl3`
- `windows_amd64`
- `macos_amd64`

Advanced: If you want to use a different precompiled native, please set the java property `it.tdlight.native.workdir`. (Please note that you must build [this](https://github.com/tdlight-team/tdlight-java-natives), you can't put random precompiled tdlib binaries found on the internet)

## Usage

An example on how to use TDLight Java can be found
here: [Example.java](https://github.com/tdlight-team/tdlight-java/blob/master/example/src/main/java/it/tdlight/example/Example.java)

### Advanced usage

If you want to disable the automatic runtime shutdown hook, you should set the property `it.tdlight.enableShutdownHooks`
to `false`

### TDLight methods documentation

[TdApi JavaDoc](https://tdlight-team.github.io/tdlight-docs)

### TDLight extended features

TDLight has some extended features compared to TDLib, that you can see on
the [TDLight official repository](https://github.com/tdlight-team/tdlight#tdlight-extra-features).

## About

### **License**

TDLight is licensed by Andrea Cavalli <andrea@cavallium.it> under the terms of the GNU Lesser General Public License 3

### **Libraries licenses**

JTDLib is licensed by Ernesto Castellotti <erny.castell@gmail.com> under the terms of the GNU Lesser General Public
License 3

TDLib is licensed by Aliaksei Levin <levlam@telegram.org> and Arseny Smirnov <arseny30@gmail.com> under the terms of the
Boost Software License

OpenSSL is licensed under the terms of Apache License v2

Zlib is licensed under the terms of Zlib license
