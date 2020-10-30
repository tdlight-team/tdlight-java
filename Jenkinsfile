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
				//sh "mkdir -p \"${workspace}/tdlight-java/src/main\""
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
		
		stage("Build & Deploy") {
			parallel {
				stage("Deploy SNAPSHOT") {
					agent {
						docker {
							image 'maven:3.6.3-openjdk-11'
							args '-v $HOME:/var/maven'
							reuseNode true
						}
					}
					steps {
						sh "cd tdlib; mvn -s $MVN_SET -B -PsnapshotDir deploy"
						sh "cd tdlight; mvn -s $MVN_SET -B -PsnapshotDir deploy"
					}
				}

				stage("Release") {
					stages {
						stage("Deploy Release") {
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
								sh "cd ${workspace}"
								sh "git add --all || true"
								sh "git commit -m \"Add generated files\" || true"
								sh "cd tdlib; mvn -B -s $MVN_SET -Drevision=${BUILD_NUMBER} -PreleaseDir clean deploy"
								sh "cd tdlight; mvn -B -s $MVN_SET -Drevision=${BUILD_NUMBER} -PreleaseDir clean deploy"
							}
						}

						stage("Publish Javadocs") {
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
								withCredentials([usernamePassword(credentialsId: "gitignuranzapassword", usernameVariable: 'USER', passwordVariable: 'PASS')]) {
										script {
												env.encodedPass=URLEncoder.encode(PASS, "UTF-8")
										}
										sh "\
											set -e; \
											cd tdlight/target-release/apidocs; \
											find . -name '*.html' -exec sed -i -r 's/<\\/title>/<\\/title>\\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>\\n\\t#memberSummary_tabpanel{overflow-x: auto;}\\n\\tli.blockList{overflow-x: auto;}\\n\\ttd.colLast div{max-width:30vw;}\\n\\t#search{width: 400px;max-width: 65vw;}\\n\\t.title,.subTitle,pre,.inheritance,h1,h2,h3,h4,h5,.memberSummary,.memberSignature,.typeSummary,.blockList{white-space:normal;word-break:break-word;}\\n<\\/style>/' {} \\;; \
											git init; \
											git remote add origin https://${USER}:${encodedPass}@git.ignuranza.net/tdlight-team/tdlight-docs; \
											git config user.email \"andrea@warp.ovh\"; \
											git config user.name \"Andrea Cavalli\"; \
											git add -A; \
											git commit -m \"Update javadocs\"; \
											git push --set-upstream origin master --force; \
											"
								}
							}
						}
					}
				}
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
