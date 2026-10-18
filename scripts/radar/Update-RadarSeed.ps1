# Refreshes the radar copy shipped in the APK (app/src/main/assets/radar/{osm,antt}.csv.gzip)
# from OpenStreetMap (Overpass) and ANTT, using the app's own Kotlin download/parse code
# (RadarSeedGenerator). Run before a release; the car refreshes itself daily afterwards.
#
#   powershell -File scripts/radar/Update-RadarSeed.ps1
param(
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr",
    # A PC whose antivirus intercepts HTTPS (AVG) needs its root in the JVM truststore; the
    # build already keeps one here (scripts/Setup-JavaSslForAvg.ps1). Skipped when missing.
    [string]$TrustStore = (Join-Path $HOME ".gradle\haval-ssl\cacerts-with-avg")
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$env:JAVA_HOME = $JavaHome
$env:RADAR_SEED_OUT = Join-Path $root "app\src\main\assets\radar"
$hadToolOptions = Test-Path Env:JAVA_TOOL_OPTIONS
if (Test-Path $TrustStore) {
    # Inherited by the test JVM that does the downloading.
    $env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStore=$TrustStore -Djavax.net.ssl.trustStorePassword=changeit"
}
Push-Location $root
try {
    # --rerun: the generator is a test, so Gradle would otherwise skip it as up to date.
    .\gradlew.bat :app:testDebugUnitTest --tests "*RadarSeedGenerator*" --rerun -i | Select-String "radar seed:"
    Get-ChildItem $env:RADAR_SEED_OUT | Format-Table Name, Length
} finally {
    Pop-Location
    Remove-Item Env:RADAR_SEED_OUT
    if (-not $hadToolOptions) { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
}
