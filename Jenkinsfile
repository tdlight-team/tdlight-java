#!/usr/bin/env groovy
// see https://jenkins.io/doc/book/pipeline/syntax/

pipeline {
	environment {
		MVN_SET = credentials('maven_settings')
		JAVA_TOOL_OPTIONS = '-Duser.home=/var/maven'
	}
	agent any
	options {
		timestamps()
		ansiColor("xterm")
	}
	parameters {
		booleanParam(name: "RELEASE",
			description: "Build a release from current commit.",
			defaultValue: false)
	}
	stages {
		stage("Setup workspace") {
			agent none
			steps {
				sh "mkdir -p \"/var/jenkins_cache/.m2\""
				sh "chown 1000:1000 -R \"/var/jenkins_cache/.m2\""
				sh "mkdir -p \"/var/jenkins_cache/.ccache\""
				sh "chown 1000:1000 -R \"/var/jenkins_cache/.ccache\""
				sh "mkdir -p \"${workspace}/tdlight-java/src/main\""
				//sh "chown 1000:1000 -R \"${workspace}\""
				//sh "chmod 771 -R \"${workspace}\""
			}
		}

		stage("Generate TdApi.java") {
			agent {
				dockerfile {
					dir 'jenkins/docker'
					filename 'dockerfile'
					additionalBuildArgs '--build-arg version=1.0.0 --build-arg UID=1000 --build-arg GID=1000 --build-arg UNAME=jenkins'
					args "-v \"${workspace}/src/main:/home/jenkins/output:rw\" -v \"/var/jenkins_cache/.m2:/home/jenkins/.m2:rw\" -v \"/var/jenkins_cache/.ccache:/home/jenkins/.ccache:rw\" -v \"${workspace}:/home/jenkins/work:rw\""
					reuseNode true
				}
			}
			steps {
				sh "./jenkins/scripts/generate_tdapi.sh"
			}
		}

		stage("Build & Deploy SNAPSHOT") {
			agent {
				docker {
					image 'maven:3.6.3-openjdk-11'
					args '-v $HOME:/var/maven'
					reuseNode true
				}
			}
			steps {
				sh "mvn -s $MVN_SET -B deploy"
			}
		}

		stage("Release") {
			agent {
				docker {
					image 'maven:3.6.3-openjdk-11'
					args '-v $HOME:/var/maven'
					reuseNode true
				}
			}
			when {
				expression { params.RELEASE }
			}
			steps {
				sh "git config user.email \"jenkins@mchv.eu\""
				sh "git config user.name \"Jenkins\""
				sh "git add --all; git commit -m \"Add generated files\""
				sh "mvn -s $MVN_SET -DpushChanges=false -DlocalCheckout=true -DpreparationGoals=initialize release:prepare release:perform -B"
			}
		}
	}
	post {
		always {
			/* clean up directory */
			deleteDir()
		}
	}
}
