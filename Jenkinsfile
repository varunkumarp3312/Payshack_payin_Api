pipeline {
    agent any

    tools {
        maven 'Maven'
         jdk 'JDK 21'
    }

    stages {

        stage('Checkout Code') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/varunkumarp3312/Payshack_payin_Api.git'
            }
        }

        stage('Run API Tests') {
            steps {
                /*
                 * -Dmaven.test.failure.ignore=true allows Maven to continue
                 * even when some tests fail.
                 *
                 * Jenkins JUnit report will still show failed test cases.
                 */
                bat 'mvn clean test surefire-report:report -Dmaven.test.failure.ignore=true'
            }
        }

        stage('Publish Test Reports') {
            steps {
                junit allowEmptyResults: true,
                      testResults: 'target/surefire-reports/*.xml'

                archiveArtifacts artifacts: 'target/surefire-reports/**',
                                 allowEmptyArchive: true
            }
        }

        stage('Publish HTML Report') {
            steps {
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/reports',
                    reportFiles: 'surefire.html',
                    reportName: 'API Test HTML Report'
                ])
            }
        }
    }

    post {
        always {
            echo 'API test execution completed.'
        }

        success {
            echo 'All API tests passed.'
        }

        unstable {
            echo 'Some API tests failed. Check Jenkins Test Result report.'
        }

        failure {
            echo 'Pipeline failed due to build or configuration issue.'
        }
    }
}