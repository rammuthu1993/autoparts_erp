[CmdletBinding()]
param(
    [switch]$FocusedOnly,
    [string]$ReportPath = "target/phase5-verification-summary.txt"
)

$ErrorActionPreference = "Stop"

function Write-Section {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor DarkCyan
    Write-Host $Message -ForegroundColor Cyan
    Write-Host ("=" * 72) -ForegroundColor DarkCyan
}

function Get-SurefireTotals {
    param([Parameter(Mandatory = $true)][string]$Directory)

    $files = @(Get-ChildItem -Path $Directory -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)

    [int]$tests = 0
    [int]$failures = 0
    [int]$errors = 0
    [int]$skipped = 0

    foreach ($file in $files) {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($file.FullName)
        $root = $document.DocumentElement

        if ($null -eq $root) {
            throw "Surefire XML has no root element: $($file.FullName)"
        }

        if ($root.LocalName -eq "testsuite") {
            $suites = @($root)
        }
        elseif ($root.LocalName -eq "testsuites") {
            if ($root.HasAttribute("tests")) {
                $tests += [int]$root.GetAttribute("tests")
                $failures += [int]$root.GetAttribute("failures")
                $errors += [int]$root.GetAttribute("errors")
                $skipped += [int]$root.GetAttribute("skipped")
                continue
            }
            $suites = @($root.ChildNodes | Where-Object {
                $_ -is [System.Xml.XmlElement] -and $_.LocalName -eq "testsuite"
            })
        }
        else {
            throw "Unexpected XML root '$($root.LocalName)' in $($file.FullName)"
        }

        foreach ($suite in $suites) {
            $tests += [int]$suite.GetAttribute("tests")
            $failures += [int]$suite.GetAttribute("failures")
            $errors += [int]$suite.GetAttribute("errors")
            $skipped += [int]$suite.GetAttribute("skipped")
        }
    }

    [pscustomobject]@{
        Files = $files.Count
        Tests = $tests
        Failures = $failures
        Errors = $errors
        Skipped = $skipped
    }
}

function Invoke-MavenStage {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogPath
    )

    Write-Section $Label
    Write-Host "Command: .\mvnw.cmd $($Arguments -join ' ')"
    Write-Host "Log: $LogPath"

    & .\mvnw.cmd @Arguments 2>&1 |
        Tee-Object -FilePath $LogPath |
        Out-Host

    $code = $LASTEXITCODE
    if ($null -eq $code) { return 1 }
    return [int]$code
}

function Write-VerificationReport {
    param(
        [string]$Path,
        [datetime]$StartedAt,
        [string]$JavaVersion,
        [string]$MavenVersion,
        [bool]$FocusedOnlyMode,
        $FocusedExitCode,
        $FullExitCode,
        $FocusedTotals,
        $FullTotals,
        [int]$OverallExitCode,
        [string]$FailureMessage
    )

    $finishedAt = Get-Date
    $lines = @(
        "Phase 5 Maven verification (report-isolated V4)",
        "Started:  $($StartedAt.ToString('yyyy-MM-dd HH:mm:ss zzz'))",
        "Finished: $($finishedAt.ToString('yyyy-MM-dd HH:mm:ss zzz'))",
        "Java: $JavaVersion",
        "Maven: $MavenVersion",
        "Focused-only mode: $FocusedOnlyMode",
        "",
        "Focused test stage:",
        "  Exit code: $(if ($null -eq $FocusedExitCode) { 'Not run' } else { $FocusedExitCode })",
        "  XML files: $(if ($null -eq $FocusedTotals) { 'Not available' } else { $FocusedTotals.Files })",
        "  Tests:     $(if ($null -eq $FocusedTotals) { 'Not available' } else { $FocusedTotals.Tests })",
        "  Failures:  $(if ($null -eq $FocusedTotals) { 'Not available' } else { $FocusedTotals.Failures })",
        "  Errors:    $(if ($null -eq $FocusedTotals) { 'Not available' } else { $FocusedTotals.Errors })",
        "  Skipped:   $(if ($null -eq $FocusedTotals) { 'Not available' } else { $FocusedTotals.Skipped })",
        "",
        "Full regression stage:",
        "  Exit code: $(if ($null -eq $FullExitCode) { 'Not run' } else { $FullExitCode })",
        "  XML files: $(if ($null -eq $FullTotals) { 'Not available' } else { $FullTotals.Files })",
        "  Tests:     $(if ($null -eq $FullTotals) { 'Not available' } else { $FullTotals.Tests })",
        "  Failures:  $(if ($null -eq $FullTotals) { 'Not available' } else { $FullTotals.Failures })",
        "  Errors:    $(if ($null -eq $FullTotals) { 'Not available' } else { $FullTotals.Errors })",
        "  Skipped:   $(if ($null -eq $FullTotals) { 'Not available' } else { $FullTotals.Skipped })",
        "",
        "Overall exit code: $OverallExitCode",
        "Result: $(if ($OverallExitCode -eq 0) { 'PASS' } else { 'FAIL' })",
        ""
    )

    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        $lines += @("Failure details:", $FailureMessage, "")
    }

    $lines += @(
        "Logs:",
        "  logs/phase5-focused-tests.log",
        "  logs/phase5-full-regression.log",
        "",
        "Archived focused Surefire XML:",
        "  phase5-artifacts/focused-surefire-reports/",
        "",
        "Interpretation:",
        "- Surefire counts are read from XML reports produced by this run.",
        "- Focused XML and logs are stored outside target so Maven clean cannot delete them.",
        "- This report does not certify live-provider quality, production database permissions,",
        "  representative HTTP p95 latency, multi-instance coordination, or production readiness."
    )

    $parent = Split-Path -Parent $Path
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    $lines | Set-Content -LiteralPath $Path -Encoding UTF8
}

if (-not (Test-Path -LiteralPath ".\pom.xml" -PathType Leaf) -or
    -not (Test-Path -LiteralPath ".\mvnw.cmd" -PathType Leaf)) {
    throw "Run this script from the autoparts-erp project root."
}

# Keep all logs and archived reports OUTSIDE target: mvnw clean deletes target.
$logsDirectory = ".\logs"
$artifactsDirectory = ".\phase5-artifacts"
$focusedArchive = Join-Path $artifactsDirectory "focused-surefire-reports"
$focusedLog = Join-Path $logsDirectory "phase5-focused-tests.log"
$fullLog = Join-Path $logsDirectory "phase5-full-regression.log"

New-Item -ItemType Directory -Force -Path $logsDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $artifactsDirectory | Out-Null
if (Test-Path $focusedArchive) {
    Remove-Item -LiteralPath $focusedArchive -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $focusedArchive | Out-Null

$startedAt = Get-Date
$javaVersionText = "Not validated"
$mavenVersionText = "Not validated"
$focusedExit = $null
$fullExit = $null
$focusedTotals = $null
$fullTotals = $null
$overallExit = 0
$failureMessage = $null

$focusedTests = @(
    "ErpAiDomainEvaluationTest",
    "ErpAiConcurrencyLoadTest",
    "ErpAiSqlInjectionDefenseTest",
    "ErpAiSecurityAuthorizationTest",
    "ErpAiAsyncSecurityIsolationTest",
    "PromptInjectionDefenseTest",
    "ErpAiComparisonAndContextTest",
    "ErpAiPendingClarificationStoreTest",
    "KnowledgeQueryPreprocessorTest",
    "KnowledgeRetrievalEngineTest",
    "CitationValidatorTest",
    "AiMetricsServiceTest",
    "LiveGroundedAnswerEvaluationServiceTest"
) -join ","

try {
    Write-Section "Phase 5 environment validation"

    $javaLines = & $env:ComSpec /d /c "java -version 2>&1"
    $javaCode = $LASTEXITCODE
    $javaOutput = $javaLines -join [Environment]::NewLine
    $javaVersionText = $javaOutput.Trim()
    Write-Host $javaOutput

    if ($javaCode -ne 0) { throw "java -version failed with exit code $javaCode." }
    if ($javaOutput -notmatch 'version "25(?:\.|")') {
        throw "Java 25 is required. Current detected version does not match."
    }

    $mavenOutput = (& .\mvnw.cmd -version 2>&1 | Out-String)
    $mavenCode = $LASTEXITCODE
    $mavenVersionText = ($mavenOutput -split "`r?`n" |
        Where-Object { $_ -match 'Apache Maven|Java version:|Maven home:' }) -join "; "
    Write-Host $mavenOutput

    if ($mavenCode -ne 0) { throw "Maven Wrapper version check failed: $mavenCode." }
    if ($mavenOutput -notmatch 'Java version:\s*25') {
        throw "Maven Wrapper must run with Java 25."
    }

    # Remove stale Surefire XML from any earlier full-suite run.
    # Without this, a focused run can accidentally aggregate old reports (for example,
    # 852 from a previous full run) and falsely display that as the focused count.
    $focusedSourceReports = ".\target\surefire-reports"
    if (Test-Path -LiteralPath $focusedSourceReports) {
        Remove-Item -LiteralPath $focusedSourceReports -Recurse -Force
    }
    Write-Host "Cleared stale Surefire reports before focused stage: $focusedSourceReports"

    # Start focused run with normal Surefire output directory.
    # No custom surefire.reportsDirectory property is passed.
    $focusedExit = Invoke-MavenStage `
        -Label "Focused Phase 5 deterministic regression" `
        -Arguments @("-Dtest=$focusedTests", "test") `
        -LogPath $focusedLog

    # Only XML generated by this focused invocation remains in this directory.
    $focusedTotals = Get-SurefireTotals -Directory $focusedSourceReports

    if ($focusedTotals.Files -gt 0) {
        Copy-Item -Path (Join-Path $focusedSourceReports "TEST-*.xml") `
            -Destination $focusedArchive -ErrorAction Stop
    }

    if ($focusedExit -ne 0) {
        throw "Focused Phase 5 tests failed with Maven exit code $focusedExit."
    }
    if ($focusedTotals.Files -eq 0) {
        throw "Maven succeeded but no TEST-*.xml reports were found in target/surefire-reports. Check the focused log and Maven Surefire configuration."
    }
    if ($focusedTotals.Failures -gt 0 -or $focusedTotals.Errors -gt 0) {
        throw "Focused tests contain $($focusedTotals.Failures) failure(s) and $($focusedTotals.Errors) error(s)."
    }

    if (-not $FocusedOnly) {
        # Maven clean removes target, but logs and archived focused XML are safe.
        $fullExit = Invoke-MavenStage `
            -Label "Full clean Maven regression" `
            -Arguments @("clean", "test") `
            -LogPath $fullLog

        $fullTotals = Get-SurefireTotals -Directory ".\target\surefire-reports"

        if ($fullExit -ne 0) {
            throw "Full clean Maven regression failed with exit code $fullExit."
        }
        if ($fullTotals.Files -eq 0) {
            throw "Full regression produced no TEST-*.xml reports in target/surefire-reports."
        }
        if ($fullTotals.Failures -gt 0 -or $fullTotals.Errors -gt 0) {
            throw "Full regression contains $($fullTotals.Failures) failure(s) and $($fullTotals.Errors) error(s)."
        }
    }
}
catch {
    $overallExit = 1
    $failureMessage = $_.Exception.Message
    Write-Host ""
    Write-Host "VERIFICATION FAILED: $failureMessage" -ForegroundColor Red
}
finally {
    try {
        Write-VerificationReport `
            -Path $ReportPath `
            -StartedAt $startedAt `
            -JavaVersion $javaVersionText `
            -MavenVersion $mavenVersionText `
            -FocusedOnlyMode ([bool]$FocusedOnly) `
            -FocusedExitCode $focusedExit `
            -FullExitCode $fullExit `
            -FocusedTotals $focusedTotals `
            -FullTotals $fullTotals `
            -OverallExitCode $overallExit `
            -FailureMessage $failureMessage

        Write-Host ""
        Write-Host "Summary: $ReportPath" -ForegroundColor Green
        Write-Host "Focused log: $focusedLog"
        Write-Host "Full log: $fullLog"
        Write-Host "Focused XML archive: $focusedArchive"
    }
    catch {
        Write-Host "Could not write summary: $($_.Exception.Message)" -ForegroundColor Red
        $overallExit = 1
    }
}

if ($overallExit -ne 0) { exit $overallExit }

Write-Section "Phase 5 Maven verification completed successfully"
if ($null -ne $focusedTotals) {
    Write-Host "Focused: $($focusedTotals.Tests) tests; failures=$($focusedTotals.Failures); errors=$($focusedTotals.Errors); skipped=$($focusedTotals.Skipped)"
}
if ($null -ne $fullTotals) {
    Write-Host "Full: $($fullTotals.Tests) tests; failures=$($fullTotals.Failures); errors=$($fullTotals.Errors); skipped=$($fullTotals.Skipped)"
}
exit 0
