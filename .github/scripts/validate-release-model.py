#!/usr/bin/env python3

import argparse
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
NS = {"m": MAVEN_NAMESPACE}
EXPECTED_NATIVE_CLASSIFIERS = {
	"linux_amd64_clang_ssl3",
	"linux_amd64_gnu_ssl1",
	"linux_amd64_gnu_ssl3",
	"linux_arm64_clang_ssl3",
	"linux_arm64_gnu_ssl1",
	"linux_arm64_gnu_ssl3",
	"linux_armhf_gnu_ssl1",
	"linux_armhf_gnu_ssl3",
	"linux_ppc64el_gnu_ssl3",
	"linux_riscv64_gnu_ssl3",
	"windows_amd64",
	"macos_arm64",
}
PUBLISHED_PARENT_PROPERTIES = {
	"zxing.version",
	"reactive.streams.version",
	"slf4j.api.version",
	"junit.jupiter.engine.version",
	"fastutil.core.version",
}


def fail(message: str) -> None:
	print(f"release model validation failed: {message}", file=sys.stderr)
	raise SystemExit(1)


def parse_pom(path: Path) -> ET.Element:
	try:
		return ET.parse(path).getroot()
	except (OSError, ET.ParseError) as error:
		fail(f"can't parse {path}: {error}")


def text(element: ET.Element, path: str) -> str:
	value = element.findtext(path, default="", namespaces=NS)
	return value.strip()


def dependencies(element: ET.Element, path: str = "./m:dependencies/m:dependency") -> list[ET.Element]:
	return element.findall(path, NS)


def tdlight_api_dependencies(element: ET.Element, path: str = "./m:dependencies/m:dependency") -> list[ET.Element]:
	return [
		dependency
		for dependency in dependencies(element, path)
		if text(dependency, "m:groupId") == "it.tdlight"
		and text(dependency, "m:artifactId") == "tdlight-api"
	]


def dependency_classifier(dependency: ET.Element) -> str:
	return text(dependency, "m:classifier")


def dependency_type(dependency: ET.Element) -> str:
	return text(dependency, "m:type") or "jar"


def project_group_id(project: ET.Element) -> str:
	return text(project, "m:groupId") or text(project, "m:parent/m:groupId")


def project_version(project: ET.Element) -> str:
	return text(project, "m:version") or text(project, "m:parent/m:version")


def validate_project_coordinates(
		project: ET.Element,
		artifact_id: str,
		expected_version: str,
		packaging: str,
) -> None:
	actual = (
		project_group_id(project),
		text(project, "m:artifactId"),
		project_version(project),
		text(project, "m:packaging") or "jar",
	)
	expected = ("it.tdlight", artifact_id, expected_version, packaging)
	if actual != expected:
		fail(f"published {artifact_id} coordinates are {actual}; expected {expected}")


def validate_no_revision_property(project: ET.Element, artifact_id: str) -> None:
	if "revision" in pom_properties(project):
		fail(f"published {artifact_id} must not retain the build-only revision property")


def validate_source_bom(bom: ET.Element) -> tuple[str, str]:
	api_version = text(bom, "m:properties/m:tdlight.api.version")
	natives_version = text(bom, "m:properties/m:tdlight.natives.version")
	if not api_version or not natives_version:
		fail("BOM must define tdlight.api.version and tdlight.natives.version")
	return api_version, natives_version


def validate_published_bom(
		bom: ET.Element,
		api_version: str,
		natives_version: str,
		expected_version: str,
) -> None:
	validate_project_coordinates(bom, "tdlight-java-bom", expected_version, "pom")
	validate_no_revision_property(bom, "tdlight-java-bom")
	managed = dependencies(bom, "./m:dependencyManagement/m:dependencies/m:dependency")
	api_dependencies = [
		dependency
		for dependency in managed
		if text(dependency, "m:groupId") == "it.tdlight"
		and text(dependency, "m:artifactId") == "tdlight-api"
	]
	actual_api = {
		(dependency_classifier(dependency), text(dependency, "m:version"))
		for dependency in api_dependencies
	}
	expected_api = {("", api_version), ("legacy", api_version)}
	if len(api_dependencies) != len(expected_api) or actual_api != expected_api:
		fail(f"published BOM API variants are {actual_api}; expected {expected_api}")

	wrapper_dependencies = [
		dependency
		for dependency in managed
		if text(dependency, "m:groupId") == "it.tdlight"
		and text(dependency, "m:artifactId") in {"tdlight-java", "tdlight-java-parent"}
	]
	actual_wrappers = {
		(
			text(dependency, "m:artifactId"),
			dependency_classifier(dependency),
			dependency_type(dependency),
			text(dependency, "m:version"),
		)
		for dependency in wrapper_dependencies
	}
	expected_wrappers = {
		("tdlight-java", "", "jar", expected_version),
		("tdlight-java", "jdk8", "jar", expected_version),
		("tdlight-java-parent", "", "pom", expected_version),
	}
	if len(wrapper_dependencies) != len(expected_wrappers) or actual_wrappers != expected_wrappers:
		fail(
			f"published BOM wrapper mappings are {actual_wrappers}; "
			f"expected {expected_wrappers}"
		)

	native_dependencies = [
		dependency
		for dependency in managed
		if text(dependency, "m:groupId") == "it.tdlight"
		and text(dependency, "m:artifactId") == "tdlight-natives"
	]
	native_classifiers = {dependency_classifier(dependency) for dependency in native_dependencies}
	if len(native_dependencies) != len(EXPECTED_NATIVE_CLASSIFIERS) \
			or native_classifiers != EXPECTED_NATIVE_CLASSIFIERS:
		fail(
			"published BOM native classifiers are "
			f"{sorted(native_classifiers)}; expected {sorted(EXPECTED_NATIVE_CLASSIFIERS)}"
		)
	for dependency in native_dependencies:
		if text(dependency, "m:version") != natives_version:
			fail(
				f"published BOM native {dependency_classifier(dependency)} has version "
				f"{text(dependency, 'm:version')!r}; expected {natives_version!r}"
			)


def pom_properties(element: ET.Element) -> dict[str, str]:
	properties = element.find("./m:properties", NS)
	if properties is None:
		return {}
	return {
		child.tag.rsplit("}", 1)[-1]: (child.text or "").strip()
		for child in properties
	}


def validate_published_parent(source: ET.Element, published: ET.Element, expected_version: str) -> None:
	validate_project_coordinates(published, "tdlight-java-parent", expected_version, "pom")
	validate_no_revision_property(published, "tdlight-java-parent")
	source_properties = pom_properties(source)
	published_properties = pom_properties(published)
	for property_name in PUBLISHED_PARENT_PROPERTIES:
		expected = source_properties.get(property_name, "")
		actual = published_properties.get(property_name, "")
		if not expected or actual != expected:
			fail(
				f"published parent property {property_name} is {actual!r}; expected {expected!r}"
			)


def validate_flattened_pom(path: Path, api_version: str, expected_version: str) -> None:
	root = parse_pom(path)
	validate_project_coordinates(root, "tdlight-java", expected_version, "jar")
	validate_no_revision_property(root, "tdlight-java")
	parent = (
		text(root, "m:parent/m:groupId"),
		text(root, "m:parent/m:artifactId"),
		text(root, "m:parent/m:version"),
	)
	expected_parent = ("it.tdlight", "tdlight-java-parent", expected_version)
	if parent != expected_parent:
		fail(f"{path} parent is {parent}; expected {expected_parent}")
	bom_imports = [
		dependency
		for dependency in dependencies(root, "./m:dependencyManagement/m:dependencies/m:dependency")
		if text(dependency, "m:groupId") == "it.tdlight"
		and text(dependency, "m:artifactId") == "tdlight-java-bom"
	]
	if len(bom_imports) != 1:
		fail(f"{path} must import exactly one tdlight-java-bom")
	bom_import = bom_imports[0]
	actual_import = (
		text(bom_import, "m:version"),
		dependency_type(bom_import),
		text(bom_import, "m:scope"),
	)
	expected_import = (expected_version, "pom", "import")
	if actual_import != expected_import:
		fail(f"{path} BOM import is {actual_import}; expected {expected_import}")
	api_dependencies = tdlight_api_dependencies(root)
	if len(api_dependencies) != 1:
		fail(f"{path} must expose exactly one top-level tdlight-api dependency")
	api_dependency = api_dependencies[0]
	if dependency_classifier(api_dependency) != "${tdlight.api.classifier}":
		fail(f"{path} must select tdlight-api through tdlight.api.classifier")
	if text(api_dependency, "m:version") != "${tdlight.api.version}":
		fail(f"{path} must select tdlight-api through tdlight.api.version")
	properties = pom_properties(root)
	if properties.get("tdlight.api.version") != api_version:
		fail(f"{path} publishes the wrong tdlight.api.version")
	if properties.get("tdlight.api.classifier", ""):
		fail(f"{path} must default tdlight.api.classifier to the unclassified API")

	profiles = {
		text(profile, "m:id"): profile
		for profile in root.findall("./m:profiles/m:profile", NS)
	}
	for profile_id, expected_classifier in (("java8", "legacy"), ("java17", "")):
		profile = profiles.get(profile_id)
		if profile is None:
			fail(f"{path} is missing the {profile_id} consumer profile")
		if tdlight_api_dependencies(profile):
			fail(f"{profile_id} must override a property, not add another tdlight-api dependency")
		actual_classifier = text(profile, "m:properties/m:tdlight.api.classifier")
		if actual_classifier != expected_classifier:
			fail(
				f"{profile_id} selected classifier {actual_classifier!r}; "
				f"expected {expected_classifier!r}"
			)

	artifact_profile = profiles.get("jdk8-artifact")
	if artifact_profile is None:
		fail(f"{path} is missing the jdk8-artifact packaging profile")
	if tdlight_api_dependencies(artifact_profile):
		fail("jdk8-artifact must not select an API dependency")

	raw_pom = path.read_text(encoding="utf-8")
	if "${revision}" in raw_pom:
		fail(f"{path} contains an unresolved revision placeholder")


def validate_effective_pom(path: Path, expected_classifier: str, api_version: str) -> None:
	root = parse_pom(path)
	api_dependencies = tdlight_api_dependencies(root)
	if len(api_dependencies) != 1:
		fail(f"{path} must have exactly one active top-level tdlight-api dependency")
	api_dependency = api_dependencies[0]
	actual_classifier = dependency_classifier(api_dependency)
	if actual_classifier != expected_classifier:
		fail(
			f"{path} selected classifier {actual_classifier!r}; "
			f"expected {expected_classifier!r}"
		)
	actual_version = text(api_dependency, "m:version")
	if actual_version != api_version:
		fail(f"{path} selected tdlight-api {actual_version!r}; expected {api_version!r}")


def validate_generated_version(path: Path, natives_version: str) -> None:
	try:
		generated_source = path.read_text(encoding="utf-8")
	except OSError as error:
		fail(f"can't read {path}: {error}")
	expected = f'NATIVES_VERSION = "{natives_version}"'
	if expected not in generated_source:
		fail(f"{path} does not contain {expected!r}")


def class_major_version(archive: zipfile.ZipFile, entry: str) -> int:
	try:
		class_bytes = archive.read(entry)
	except KeyError:
		fail(f"{archive.filename} is missing {entry}")
	if len(class_bytes) < 8 or class_bytes[:4] != b"\xca\xfe\xba\xbe":
		fail(f"{archive.filename}!/{entry} is not a Java class")
	return struct.unpack(">H", class_bytes[6:8])[0]


def validate_artifacts(artifact_directory: Path) -> None:
	jars = list(artifact_directory.glob("tdlight-java-*.jar"))
	jdk8_jars = [jar for jar in jars if jar.name.endswith("-jdk8.jar")]
	main_jars = [
		jar
		for jar in jars
		if not jar.name.endswith(("-jdk8.jar", "-sources.jar", "-javadoc.jar"))
	]
	if len(main_jars) != 1 or len(jdk8_jars) != 1:
		fail(
			f"expected one main and one jdk8 JAR in {artifact_directory}; "
			f"found main={main_jars}, jdk8={jdk8_jars}"
		)

	main_jar = main_jars[0]
	jdk8_jar = jdk8_jars[0]
	for jar_path, expect_module in ((main_jar, True), (jdk8_jar, False)):
		with zipfile.ZipFile(jar_path) as archive:
			manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
			if "Multi-Release: true" not in manifest:
				fail(f"{jar_path} is missing the Multi-Release manifest attribute")
			has_module = "module-info.class" in archive.namelist()
			if has_module != expect_module:
				fail(f"{jar_path} module-info presence is {has_module}; expected {expect_module}")
			major = class_major_version(archive, "it/tdlight/Init.class")
			if major != 52:
				fail(f"{jar_path} base classes target major {major}; expected Java 8 major 52")

	described_module = subprocess.run(
		["jar", "--describe-module", "--file", str(main_jar)],
		check=True,
		capture_output=True,
		text=True,
	).stdout
	provider = (
		"provides reactor.blockhound.integration.BlockHoundIntegration "
		"with it.tdlight.util.TDLightBlockHoundIntegration"
	)
	if provider not in described_module:
		fail(f"{main_jar} does not declare the BlockHound service provider")


def run_gradle_probe(gradle_command: str, build_script: str) -> set[str]:
	with tempfile.TemporaryDirectory(prefix="tdlight-gradle-consumer-") as directory:
		project_directory = Path(directory)
		(project_directory / "settings.gradle").write_text(
			"rootProject.name = 'tdlight-consumer-probe'\n",
			encoding="utf-8",
		)
		(project_directory / "build.gradle").write_text(build_script, encoding="utf-8")
		source_directory = project_directory / "src" / "main" / "java"
		source_directory.mkdir(parents=True)
		(source_directory / "TdlightConsumerProbe.java").write_text(
			"""import it.tdlight.ClientFactory;
import it.tdlight.jni.TdApi;

final class TdlightConsumerProbe {
    private ClientFactory factory;
    private TdApi.Close close = new TdApi.Close();
}
""",
			encoding="utf-8",
		)
		try:
			result = subprocess.run(
				[
					gradle_command,
					"--offline",
					"--no-daemon",
					"--console=plain",
					"-q",
					"compileJava",
					"printCompileClasspath",
				],
				cwd=project_directory,
				check=True,
				capture_output=True,
				text=True,
			)
		except (OSError, subprocess.CalledProcessError) as error:
			output = (getattr(error, "stdout", "") or "") + (getattr(error, "stderr", "") or "")
			fail(f"Gradle consumer probe failed: {error}\n{output}")
	return {
		line.removeprefix("ARTIFACT:").strip()
		for line in result.stdout.splitlines()
		if line.startswith("ARTIFACT:")
	}


def validate_gradle_consumers(gradle_command: str, version: str, api_version: str) -> None:
	common = """
plugins { id 'java' }
repositories { mavenLocal() }
tasks.register('printCompileClasspath') {
    doLast {
        configurations.compileClasspath.resolve().collect { it.name }.sort().each {
            println 'ARTIFACT:' + it
        }
    }
}
"""
	default_artifacts = run_gradle_probe(
		gradle_command,
		common + """
dependencies {
    implementation platform('it.tdlight:tdlight-java-bom:%s')
    implementation group: 'it.tdlight', name: 'tdlight-java'
}
""" % version,
	)
	expected_default = {
		f"tdlight-java-{version}.jar",
		f"tdlight-api-{api_version}.jar",
	}
	if not expected_default.issubset(default_artifacts):
		fail(f"Gradle default consumer resolved {sorted(default_artifacts)}")
	if f"tdlight-api-{api_version}-legacy.jar" in default_artifacts:
		fail("Gradle default consumer also resolved the legacy API")

	legacy_artifacts = run_gradle_probe(
		gradle_command,
		common + """
dependencies {
    implementation platform('it.tdlight:tdlight-java-bom:%s')
    implementation(group: 'it.tdlight', name: 'tdlight-java', classifier: 'jdk8') {
        exclude group: 'it.tdlight', module: 'tdlight-api'
    }
    implementation group: 'it.tdlight', name: 'tdlight-api', classifier: 'legacy'
}
""" % version,
	)
	expected_legacy = {
		f"tdlight-java-{version}-jdk8.jar",
		f"tdlight-api-{api_version}-legacy.jar",
	}
	if not expected_legacy.issubset(legacy_artifacts):
		fail(f"Gradle legacy consumer resolved {sorted(legacy_artifacts)}")
	if f"tdlight-api-{api_version}.jar" in legacy_artifacts:
		fail("Gradle legacy consumer also resolved the default API")


def dependency_tree_coordinates(tree: str) -> list[tuple[str, str, str, str, str, str]]:
	coordinates = []
	for line in tree.splitlines():
		candidate = line.lstrip(" +-\\|")
		if not candidate:
			continue
		parts = candidate.split(None, 1)[0].split(":")
		if len(parts) == 5:
			group_id, artifact_id, dependency_type, version, scope = parts
			classifier = ""
		elif len(parts) == 6:
			group_id, artifact_id, dependency_type, classifier, version, scope = parts
		else:
			continue
		coordinates.append(
			(group_id, artifact_id, dependency_type, classifier, version, scope)
		)
	return coordinates


def validate_maven_legacy_consumer(maven_command: str, version: str, api_version: str) -> None:
	with tempfile.TemporaryDirectory(prefix="tdlight-maven-consumer-") as directory:
		project_directory = Path(directory)
		pom = project_directory / "pom.xml"
		dependency_tree = project_directory / "dependency-tree.txt"
		source_directory = project_directory / "src" / "main" / "java"
		source_directory.mkdir(parents=True)
		(source_directory / "TdlightConsumerProbe.java").write_text(
			"""import it.tdlight.ClientFactory;
import it.tdlight.jni.TdApi;

final class TdlightConsumerProbe {
    private ClientFactory factory;
    private TdApi.Close close = new TdApi.Close();
}
""",
			encoding="utf-8",
		)
		pom.write_text(
			"""<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>it.tdlight.validation</groupId>
  <artifactId>jdk8-consumer</artifactId>
  <version>1</version>
  <properties>
    <maven.compiler.release>8</maven.compiler.release>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>it.tdlight</groupId>
        <artifactId>tdlight-java-bom</artifactId>
        <version>%s</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>it.tdlight</groupId>
      <artifactId>tdlight-java</artifactId>
      <classifier>jdk8</classifier>
      <exclusions>
        <exclusion>
          <groupId>it.tdlight</groupId>
          <artifactId>tdlight-api</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>it.tdlight</groupId>
      <artifactId>tdlight-api</artifactId>
      <classifier>legacy</classifier>
    </dependency>
  </dependencies>
</project>
""" % version,
			encoding="utf-8",
		)
		try:
			subprocess.run(
				[
					maven_command,
					"-B",
					"-q",
					"-nsu",
					"-f",
					str(pom),
					"org.apache.maven.plugins:maven-dependency-plugin:3.7.0:tree",
					"org.apache.maven.plugins:maven-compiler-plugin:3.8.1:compile",
					f"-DoutputFile={dependency_tree}",
					"-DoutputType=text",
					"-Dscope=compile",
				],
				cwd=project_directory,
				check=True,
				capture_output=True,
				text=True,
			)
		except (OSError, subprocess.CalledProcessError) as error:
			output = (getattr(error, "stdout", "") or "") + (getattr(error, "stderr", "") or "")
			fail(f"Maven legacy consumer probe failed: {error}\n{output}")

		try:
			coordinates = dependency_tree_coordinates(dependency_tree.read_text(encoding="utf-8"))
		except OSError as error:
			fail(f"can't read Maven consumer dependency tree: {error}")

	tdlight_coordinates = sorted(
		coordinate
		for coordinate in coordinates
		if coordinate[0] == "it.tdlight"
		and coordinate[1] in {"tdlight-java", "tdlight-api"}
	)
	expected = sorted([
		("it.tdlight", "tdlight-java", "jar", "jdk8", version, "compile"),
		("it.tdlight", "tdlight-api", "jar", "legacy", api_version, "compile"),
	])
	if tdlight_coordinates != expected:
		fail(
			"Maven JDK 17-hosted legacy consumer resolved "
			f"{tdlight_coordinates}; expected {expected}"
		)


def main() -> None:
	parser = argparse.ArgumentParser(description="Validate the published TDLight Java release model")
	parser.add_argument("--source-bom-pom", required=True, type=Path)
	parser.add_argument("--published-bom-pom", required=True, type=Path)
	parser.add_argument("--source-parent-pom", required=True, type=Path)
	parser.add_argument("--published-parent-pom", required=True, type=Path)
	parser.add_argument("--flattened-pom", required=True, type=Path)
	parser.add_argument("--effective-pom", required=True, type=Path)
	parser.add_argument("--generated-version", required=True, type=Path)
	parser.add_argument("--expected-version", required=True)
	parser.add_argument(
		"--expected-api-classifier",
		choices=("default", "legacy"),
		required=True,
	)
	parser.add_argument("--artifact-directory", type=Path)
	parser.add_argument("--gradle-command")
	parser.add_argument("--maven-command")
	arguments = parser.parse_args()

	source_bom = parse_pom(arguments.source_bom_pom)
	published_bom = parse_pom(arguments.published_bom_pom)
	api_version, natives_version = validate_source_bom(source_bom)
	validate_published_bom(
		published_bom,
		api_version,
		natives_version,
		arguments.expected_version,
	)
	validate_published_parent(
		parse_pom(arguments.source_parent_pom),
		parse_pom(arguments.published_parent_pom),
		arguments.expected_version,
	)
	validate_flattened_pom(arguments.flattened_pom, api_version, arguments.expected_version)
	expected_classifier = "" if arguments.expected_api_classifier == "default" else "legacy"
	validate_effective_pom(arguments.effective_pom, expected_classifier, api_version)
	validate_generated_version(arguments.generated_version, natives_version)
	if arguments.artifact_directory is not None:
		validate_artifacts(arguments.artifact_directory)
	if arguments.gradle_command is not None:
		validate_gradle_consumers(arguments.gradle_command, arguments.expected_version, api_version)
	if arguments.maven_command is not None:
		validate_maven_legacy_consumer(arguments.maven_command, arguments.expected_version, api_version)
	print(
		"release model validation passed: "
		f"version={arguments.expected_version}, "
		f"api={api_version}:{arguments.expected_api_classifier}, natives={natives_version}"
	)


if __name__ == "__main__":
	main()
