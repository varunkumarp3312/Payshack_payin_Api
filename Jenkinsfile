pipeline {
    agent any

    tools {
        maven '3.9.11'
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
                sh 'mvn clean test surefire-report:report -Dmaven.test.failure.ignore=true'
            }
        }

        stage('Publish Reports And Send Failure Email') {
            steps {
                script {

                    def testSummary = junit(
                        allowEmptyResults: true,
                        testResults: 'target/surefire-reports/*.xml',
                        skipMarkingBuildUnstable: true
                    )

                    archiveArtifacts artifacts: 'target/surefire-reports/**, target/reports/**',
                                     allowEmptyArchive: true

                    echo "Total Tests  : ${testSummary.totalCount}"
                    echo "Passed Tests : ${testSummary.passCount}"
                    echo "Failed Tests : ${testSummary.failCount}"
                    echo "Skipped Tests: ${testSummary.skipCount}"

                    if (testSummary.failCount > 0) {

                        sh '''
                            mkdir -p target

                            cat > target/failed-tests.html <<'EOF'
<html>
<body>
<h2>PayShack API Automation - Failed Test Case Details</h2>
<table border="1" cellpadding="8" cellspacing="0">
<tr>
    <th>Class Name</th>
    <th>Test Case Name</th>
    <th>Failure Message</th>
</tr>
EOF

                            python3 <<'PY'
import glob
import html
import xml.etree.ElementTree as ET

rows = []

for file in glob.glob("target/surefire-reports/TEST-*.xml"):
    try:
        tree = ET.parse(file)
        root = tree.getroot()

        for testcase in root.findall("testcase"):
            failure = testcase.find("failure")
            if failure is not None:
                class_name = testcase.attrib.get("classname", "")
                test_name = testcase.attrib.get("name", "")
                failure_message = failure.attrib.get("message", "")

                if not failure_message:
                    failure_message = failure.text or ""

                rows.append(
                    "<tr>"
                    "<td>{}</td>"
                    "<td>{}</td>"
                    "<td>{}</td>"
                    "</tr>".format(
                        html.escape(class_name),
                        html.escape(test_name),
                        html.escape(failure_message)
                    )
                )
    except Exception as e:
        rows.append(
            "<tr><td colspan='3'>Unable to parse {}: {}</td></tr>".format(
                html.escape(file),
                html.escape(str(e))
            )
        )

with open("target/failed-tests.html", "a", encoding="utf-8") as f:
    if rows:
        f.write("\\n".join(rows))
    else:
        f.write("<tr><td colspan='3'>No failed test cases found in Surefire XML.</td></tr>")

    f.write("""
</table>
</body>
</html>
""")
PY
                        '''

                        def failedTestDetails = readFile('target/failed-tests.html')

                        emailext(
                            to: 'pmuaevks33@gmail.com, divyadeveloper9741@gmail.com',
                            subject: "PayShack API Automation FAILED - Build #${BUILD_NUMBER}",
                            mimeType: 'text/html',
                            body: """
                                <html>
                                    <body>
                                        <h2>PayShack API Automation Build Failed</h2>

                                        <p><b>Build Status:</b> FAILURE</p>
                                        <p><b>Job Name:</b> ${JOB_NAME}</p>
                                        <p><b>Build Number:</b> ${BUILD_NUMBER}</p>

                                        <p><b>Total Tests:</b> ${testSummary.totalCount}</p>
                                        <p><b>Passed Tests:</b> ${testSummary.passCount}</p>
                                        <p><b>Failed Tests:</b> ${testSummary.failCount}</p>
                                        <p><b>Skipped Tests:</b> ${testSummary.skipCount}</p>

                                        <p>Below are the failed test case details:</p>

                                        ${failedTestDetails}

                                        <br>

                                        <p>
                                            <b>Jenkins Build:</b>
                                            <a href="${BUILD_URL}">${BUILD_URL}</a>
                                        </p>

                                        <p>
                                            <b>Test Result:</b>
                                            <a href="${BUILD_URL}testReport/">${BUILD_URL}testReport/</a>
                                        </p>

                                        <p>
                                            <b>HTML Report:</b>
                                            HTML report files are archived under build artifacts:
                                            <br>
                                            target/reports/
                                        </p>

                                        <br>
                                        <p>Regards,<br>Jenkins Automation</p>
                                    </body>
                                </html>
                            """,
                            attachmentsPattern: 'target/failed-tests.html'
                        )

                        error("API test failures found. Email sent. Marking build as FAILURE.")
                    } else {
                        echo 'No failed test cases found. Email will not be sent.'
                    }
                }
            }
        }
    }

    post {
        always {
            echo 'API test execution completed.'
        }

        success {
            echo 'Pipeline completed successfully. No failed test cases.'
        }

        failure {
            echo 'Pipeline failed because API test failures were found or setup failed.'
        }
    }
}
