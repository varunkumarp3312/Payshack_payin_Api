stage('Publish Reports And Send Failure Email') {
    steps {
        script {

            def testSummary = junit(
                allowEmptyResults: true,
                testResults: 'target/surefire-reports/*.xml',
                skipMarkingBuildUnstable: true
            )

            archiveArtifacts artifacts: 'target/surefire-reports/**',
                             allowEmptyArchive: true

            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'target/reports',
                reportFiles: 'surefire.html',
                reportName: 'API Test HTML Report'
            ])

            echo "Total Tests  : ${testSummary.totalCount}"
            echo "Passed Tests : ${testSummary.passCount}"
            echo "Failed Tests : ${testSummary.failCount}"
            echo "Skipped Tests: ${testSummary.skipCount}"

            if (testSummary.failCount > 0) {

                powershell '''
                    $reportPath = "target\\surefire-reports"
                    $outputFile = "target\\failed-tests.html"

                    $html = @"
                    <html>
                    <body>
                    <h2>Failed Test Case Details</h2>
                    <table border='1' cellpadding='8' cellspacing='0'>
                    <tr>
                        <th>Class Name</th>
                        <th>Test Case Name</th>
                        <th>Failure Message</th>
                    </tr>
"@

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

                                $failureMessage = $failureMessage -replace "<", "&lt;"
                                $failureMessage = $failureMessage -replace ">", "&gt;"

                                $html += @"
                    <tr>
                        <td>$className</td>
                        <td>$testName</td>
                        <td>$failureMessage</td>
                    </tr>
"@
                            }
                        }
                    }

                    $html += @"
                    </table>
                    </body>
                    </html>
"@

                    $html | Out-File -FilePath $outputFile -Encoding UTF8
                '''

                def failedTestDetails = readFile('target/failed-tests.html')

                emailext(
                    to: 'varunkumarp2025@gmail.com',
                    subject: "PayShack API Automation - Failed Test Cases - Build #${BUILD_NUMBER}",
                    mimeType: 'text/html',
                    body: """
                        <html>
                            <body>
                                <h2>PayShack API Automation Report</h2>

                                <p><b>Build Status:</b> SUCCESS</p>
                                <p><b>Total Tests:</b> ${testSummary.totalCount}</p>
                                <p><b>Passed Tests:</b> ${testSummary.passCount}</p>
                                <p><b>Failed Tests:</b> ${testSummary.failCount}</p>
                                <p><b>Skipped Tests:</b> ${testSummary.skipCount}</p>

                                <p>
                                    Some API validation test cases failed.
                                    Below are the failed test case details.
                                </p>

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
                                    <a href="${BUILD_URL}API_20Test_20HTML_20Report/">
                                        Open API Test HTML Report
                                    </a>
                                </p>

                                <br>
                                <p>Regards,<br>Jenkins Automation</p>
                            </body>
                        </html>
                    """,
                    attachmentsPattern: 'target/surefire-reports/*.txt'
                )

            } else {
                echo 'No failed test cases found. Email will not be sent.'
            }
        }
    }
}