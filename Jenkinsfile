pipeline {
    agent any

    tools {
        jdk 'JDK 21'
        maven 'Maven'
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
                bat 'mvn clean test surefire-report:report -Dmaven.test.failure.ignore=true'
            }
        }

        stage('Publish Test Reports') {
            steps {
                junit(
                    allowEmptyResults: true,
                    testResults: 'target/surefire-reports/*.xml',
                    skipMarkingBuildUnstable: true
                )

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
            echo 'Pipeline completed successfully. Check Test Result for intentional failed cases.'
        }

        unstable {
            echo 'Some tests failed and Jenkins marked build unstable.'
        }

        failure {
            echo 'Pipeline failed due to setup, checkout, build, or configuration issue.'
        }
    }
}