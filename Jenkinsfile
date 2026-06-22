pipeline {
    agent any

    tools {
        maven 'Maven'
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        GIT_REPO_URL = 'https://github.com/varunkumarp3312/Payshack_payin_Api.git'
        GIT_BRANCH_NAME = 'main'

        TEST_REPORT_DIR = 'target/surefire-reports'
        MAVEN_HTML_REPORT_DIR = 'target/reports'
        FAILED_TEST_REPORT = 'target/failed-tests.html'

        EMAIL_RECIPIENTS = 'pmuaevks33@gmail.com, divyadeveloper9741@gmail.com'
    }

    stages {

        stage('Checkout Code') {
            steps {
                git branch: "${GIT_BRANCH_NAME}",
                    url: "${GIT_REPO_URL}"
            }
        }

        stage('Run API Tests') {
            steps {
                script {
                    if (isUnix()) {
                        echo 'Linux Jenkins detected. Running Maven using sh.'
                        sh 'mvn clean test surefire-report:report -Dmaven.test.failure.ignore=true'
                    } else {
                        echo 'Windows Jenkins detected. Running Maven using bat.'
                        bat 'mvn clean test surefire-report:report -Dmaven.test.failure.ignore=true'
                    }
                }
            }
        }

        stage('Publish Reports And Send Failure Email') {
            steps {
                script {

                    def testSummary = junit(
                        allowEmptyResults: true,
                        testResults: "${TEST_REPORT_DIR}/*.xml",
                        skipMarkingBuildUnstable: true
                    )

                    int totalTests = testSummary.totalCount
                    int failedTests = testSummary.failCount
                    int skippedTests = testSummary.skipCount
                    int passedTests = totalTests - failedTests - skippedTests

                    echo "Total Tests   : ${totalTests}"
                    echo "Passed Tests  : ${passedTests}"
                    echo "Failed Tests  : ${failedTests}"
                    echo "Skipped Tests : ${skippedTests}"

                    archiveArtifacts artifacts: "${TEST_REPORT_DIR}/**, ${MAVEN_HTML_REPORT_DIR}/**",
                                     allowEmptyArchive: true

                    if (failedTests > 0) {

                        if (isUnix()) {
                            echo 'Linux Jenkins detected. Creating failed test report using sh/python.'

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

                                if command -v python3 >/dev/null 2>&1; then
                                    python3 <<'PY'
import glob
import html
import xml.etree.ElementTree as ET

rows = []

for file_path in glob.glob("target/surefire-reports/TEST-*.xml"):
    try:
        tree = ET.parse(file_path)
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

    except Exception as error:
        rows.append(
            "<tr><td colspan='3'>Unable to parse {}: {}</td></tr>".format(
                html.escape(file_path),
                html.escape(str(error))
            )
        )

with open("target/failed-tests.html", "a", encoding="utf-8") as report:
    if rows:
        report.write("\\n".join(rows))
    else:
        report.write("<tr><td colspan='3'>No failed test cases found in Surefire XML.</td></tr>")

    report.write("""
</table>
</body>
</html>
""")
PY
                                else
                                    cat >> target/failed-tests.html <<'EOF'
<tr>
    <td colspan="3">python3 is not installed on Jenkins server. Unable to parse failed test details.</td>
</tr>
</table>
</body>
</html>
EOF
                                fi
                            '''

                        } else {
                            echo 'Windows Jenkins detected. Creating failed test report using PowerShell.'

                            powershell '''
                                $reportPath = "target\\surefire-reports"
                                $outputFile = "target\\failed-tests.html"

                                New-Item -ItemType Directory -Force -Path "target" | Out-Null

                                $lines = New-Object System.Collections.Generic.List[string]

                                $lines.Add("<html>")
                                $lines.Add("<body>")
                                $lines.Add("<h2>PayShack API Automation - Failed Test Case Details</h2>")
                                $lines.Add("<table border='1' cellpadding='8' cellspacing='0'>")
                                $lines.Add("<tr>")
                                $lines.Add("<th>Class Name</th>")
                                $lines.Add("<th>Test Case Name</th>")
                                $lines.Add("<th>Failure Message</th>")
                                $lines.Add("</tr>")

                                if (Test-Path $reportPath) {

                                    Get-ChildItem $reportPath -Filter "TEST-*.xml" | ForEach-Object {

                                        [xml]$xml = Get-Content $_.FullName

                                        foreach ($testcase in $xml.testsuite.testcase) {

                                            if ($testcase.failure) {

                                                $className = $testcase.classname
                                                $testName = $testcase.name
                                                $failureMessage = $testcase.failure.message

                                                if ($failureMessage -eq $null -or $failureMessage -eq "") {
                                                    $failureMessage = $testcase.failure.InnerText
                                                }

                                                $className = [System.Net.WebUtility]::HtmlEncode($className)
                                                $testName = [System.Net.WebUtility]::HtmlEncode($testName)
                                                $failureMessage = [System.Net.WebUtility]::HtmlEncode($failureMessage)

                                                $lines.Add("<tr>")
                                                $lines.Add("<td>$className</td>")
                                                $lines.Add("<td>$testName</td>")
                                                $lines.Add("<td>$failureMessage</td>")
                                                $lines.Add("</tr>")
                                            }
                                        }
                                    }

                                } else {
                                    $lines.Add("<tr><td colspan='3'>Surefire report folder not found.</td></tr>")
                                }

                                $lines.Add("</table>")
                                $lines.Add("</body>")
                                $lines.Add("</html>")

                                $lines | Set-Content -Path $outputFile -Encoding UTF8
                            '''
                        }

                        archiveArtifacts artifacts: "${FAILED_TEST_REPORT}",
                                         allowEmptyArchive: true

                        def failedTestDetails = readFile("${FAILED_TEST_REPORT}")

                        emailext(
                            to: "${EMAIL_RECIPIENTS}",
                            subject: "PayShack API Automation FAILED - Build #${BUILD_NUMBER}",
                            mimeType: 'text/html',
                            attachmentsPattern: "${FAILED_TEST_REPORT}",
                            body: """
                                <html>
                                    <body>
                                        <h2>PayShack API Automation Build Failed</h2>

                                        <p><b>Job Name:</b> ${JOB_NAME}</p>
                                        <p><b>Build Number:</b> ${BUILD_NUMBER}</p>
                                        <p><b>Build Status:</b> FAILURE</p>

                                        <table border="1" cellpadding="8" cellspacing="0">
                                            <tr>
                                                <th>Total Tests</th>
                                                <th>Passed Tests</th>
                                                <th>Failed Tests</th>
                                                <th>Skipped Tests</th>
                                            </tr>
                                            <tr>
                                                <td>${totalTests}</td>
                                                <td>${passedTests}</td>
                                                <td>${failedTests}</td>
                                                <td>${skippedTests}</td>
                                            </tr>
                                        </table>

                                        <br>

                                        <p><b>Failed Test Case Details:</b></p>

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
                                            <b>Archived Reports:</b>
                                            Available under Jenkins build artifacts.
                                        </p>

                                        <br>
                                        <p>Regards,<br>Jenkins Automation</p>
                                    </body>
                                </html>
                            """
                        )

                        error("API test failures found. Failure email sent successfully.")
                    }

                    echo 'No failed test cases found. Failure email not sent.'
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
            echo 'Pipeline failed because API test failures were found or setup issue occurred.'
        }
    }
}