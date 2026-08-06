# Sets up a Java truststore that includes AVG Web Shield's MITM root so Gradle/JBR
# can download dependencies while AVG HTTPS scanning is enabled.
#
# Root cause: curl/Windows trust AVG; Android Studio JBR cacerts does not → PKIX failures
# on services.gradle.org / plugins.gradle.org.
#
# Safe to re-run if AVG regenerates its cert (common after AVG updates).

$ErrorActionPreference = "Stop"

$jbr = "C:\Program Files\Android\Android Studio\jbr"
$keytool = Join-Path $jbr "bin\keytool.exe"
$cacerts = Join-Path $jbr "lib\security\cacerts"
$trustDir = Join-Path $env:USERPROFILE ".gradle\haval-ssl"
$outStore = Join-Path $trustDir "cacerts-with-avg"
$avgPem = "C:\ProgramData\AVG\Antivirus\wscert.pem"

if (-not (Test-Path $keytool)) { Write-Error "JBR keytool not found at $keytool" }
if (-not (Test-Path $avgPem)) { Write-Error "AVG wscert.pem not found at $avgPem — is AVG installed with HTTPS scanning?" }

New-Item -ItemType Directory -Force -Path $trustDir | Out-Null
Copy-Item $cacerts $outStore -Force

& $keytool -delete -alias "avg-shield-0" -keystore $outStore -storepass changeit -noprompt 2>$null | Out-Null
& $keytool -importcert -noprompt -alias "avg-shield-0" -file $avgPem -keystore $outStore -storepass changeit | Out-Host

$trustArgs = "-Djavax.net.ssl.trustStore=`"$outStore`" -Djavax.net.ssl.trustStorePassword=changeit"
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jbr, "User")

function Merge-Opts([string]$existing, [string]$add) {
    if ([string]::IsNullOrWhiteSpace($existing)) { return $add }
    if ($existing -like "*haval-ssl*cacerts-with-avg*") { return $existing }
    return ($existing.Trim() + " " + $add)
}

$javaOpts = Merge-Opts ([Environment]::GetEnvironmentVariable("JAVA_OPTS", "User")) $trustArgs
$gradleOpts = Merge-Opts ([Environment]::GetEnvironmentVariable("GRADLE_OPTS", "User")) $trustArgs
[Environment]::SetEnvironmentVariable("JAVA_OPTS", $javaOpts, "User")
[Environment]::SetEnvironmentVariable("GRADLE_OPTS", $gradleOpts, "User")

Write-Host ""
Write-Host "[+] Truststore: $outStore" -ForegroundColor Green
Write-Host "[+] User JAVA_HOME / JAVA_OPTS / GRADLE_OPTS updated"
Write-Host "[!] Open a NEW terminal (or restart Cursor) so agents pick up the user env vars."
Write-Host "    Or for this session only:"
Write-Host "    `$env:JAVA_HOME = '$jbr'"
Write-Host "    `$env:JAVA_OPTS = '$javaOpts'"
Write-Host "    `$env:GRADLE_OPTS = '$gradleOpts'"
