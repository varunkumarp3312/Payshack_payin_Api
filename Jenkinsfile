pipeline {
    agent any

    tools {
        maven 'Maven'
        jdk 'JDK 21'
    }

    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging'], description: 'Target environment')
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/varunkumarp3312/Payshack_payin_Api.git'
            }
        }

        stage('Run API Tests') {
            steps {
                bat "mvn clean test surefire-report:report -Denv=${params.ENV} -Dmaven.test.failure.ignore=true"
            }
        }

        stage('Publish JUnit Report') {
            steps {
                junit allowEmptyResults: true,
                      testResults: 'target/surefire-reports/*.xml'
            }
        }

        stage('Publish HTML Report') {
            steps {
                publishHTML([
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : 'target/reports',
                    reportFiles          : 'surefire.html',
                    reportName           : 'PayShack API Test Report'
                ])
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts artifacts: 'target/surefire-reports/**, target/logs/**',
                                 allowEmptyArchive: true
            }
        }
    }

    post {
        always   { echo "Pipeline finished. Environment: ${params.ENV}" }
        success  { echo 'All tests passed.' }
        unstable { echo 'Some tests failed. Review the JUnit report.' }
        failure  { echo 'Build or configuration error. Check the console log.' }
    }
}
