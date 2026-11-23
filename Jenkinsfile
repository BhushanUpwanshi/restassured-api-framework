// Declarative pipeline, same stages as GitHub Actions.
//
// Needs: a JDK 17 tool named 'jdk17', the Allure Jenkins plugin, and a username/password
// credential with id 'restful-booker' holding the API login.
pipeline {
    agent any

    tools {
        jdk 'jdk17'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        choice(name: 'ENV', choices: ['dev', 'qa', 'prod'], description: 'Maven profile and dataset set')
        booleanParam(name: 'RUN_BDD', defaultValue: true, description: 'Run the Cucumber suite')
    }

    stages {
        stage('Framework (hermetic)') {
            steps {
                sh './mvnw -B verify -pl api-core'
            }
            post {
                always {
                    junit 'api-core/target/surefire-reports/*.xml'
                    recordCoverage(tools: [[parser: 'JACOCO', pattern: 'api-core/target/site/jacoco/jacoco.xml']])
                }
            }
        }

        stage('Install') {
            steps {
                sh './mvnw -B install -DskipTests'
            }
        }

        stage('Live suites') {
            environment {
                API = credentials('restful-booker')
                API_USERNAME = "${API_USR}"
                API_PASSWORD = "${API_PSW}"
            }
            stages {
                stage('Smoke') {
                    steps {
                        sh "./mvnw -B test -pl api-tests -Dsuite=smoke -P${params.ENV}"
                    }
                }
                stage('Regression') {
                    steps {
                        sh "./mvnw -B test -pl api-tests -Dsuite=regression -P${params.ENV}"
                    }
                }
                stage('BDD') {
                    when { expression { params.RUN_BDD } }
                    steps {
                        sh "./mvnw -B test -pl api-tests -Dsuite=bdd -P${params.ENV}"
                    }
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'api-tests/target/surefire-reports/*.xml'
            allure includeProperties: false, results: [[path: 'api-tests/target/allure-results']]
            archiveArtifacts allowEmptyArchive: true, artifacts: 'api-tests/target/cucumber-report.html'
        }
    }
}
