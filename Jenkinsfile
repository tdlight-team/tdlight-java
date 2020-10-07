#!/usr/bin/env groovy
// see https://jenkins.io/doc/book/pipeline/syntax/

pipeline {
	environment {
		MVN_SET = credentials('maven_settings')
		JAVA_TOOL_OPTIONS = '-Duser.home=/var/maven'
	}
	agent any
	triggers {
		pollSCM "* * * * *"
	}
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
		stage("Build & Deploy SNAPSHOT") {
			agent {
				docker {
					image 'maven:3.6.3'
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
					image 'maven:3.6.3'
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
