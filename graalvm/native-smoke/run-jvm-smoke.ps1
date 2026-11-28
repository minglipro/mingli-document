# mingli-document GraalVM Native Image smoke example -- JVM runner (PowerShell).
#
# What it does:
#   1. compiles NativeSmoke.java against the library jars already built in this repo;
#   2. copies native-smoke META-INF/native-image metadata next to the compiled classes;
#   3. runs the smoke on the JVM (works without GraalVM) to check the example and metadata.
#
# Native image build command: see README.md in this directory.
# Prerequisite (run from the repository root):
#   .\gradlew.bat :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar
#
# Optional environment variables (used when automatic detection fails):
#   KOTLIN_STDLIB_JAR     absolute path to kotlin-stdlib-2.x.jar
#   ALIAS_FOR_JAR         absolute path to alias-for-1.0.3.jar
#   MINGLI_EXCEPTION_JAR  absolute path to mingli-exception-1.1.1-Final.jar

$ErrorActionPreference = 'Stop'

$scriptDir = $PSScriptRoot
if (-not $scriptDir) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
}
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$buildDir = Join-Path $scriptDir 'build'
$classesDir = Join-Path $buildDir 'classes'

Write-Host '== mingli-document GraalVM native smoke (JVM) =='
Write-Host "repo root : $repoRoot"

$missing = @()
$libJars = @()
foreach ($relative in @(
        'annotation\build\libs\annotation-2.2.0.jar',
        'exception\build\libs\exception-2.2.0.jar',
        'core\build\libs\core-2.2.0.jar',
        'serialization\build\libs\serialization-2.2.0.jar',
        'format-json\build\libs\format-json-2.2.0.jar'
    )) {
    $path = Join-Path $repoRoot $relative
    if (Test-Path $path) {
        $libJars += $path
    } else {
        $missing += $relative
    }
}

if ($missing.Count -gt 0) {
    Write-Host ''
    Write-Host 'Missing library jars:'
    $missing | ForEach-Object { Write-Host "  - $_" }
    Write-Host ''
    Write-Host 'Build the library modules first (from the repository root):'
    Write-Host '  .\gradlew.bat :annotation:jar :exception:jar :core:jar :serialization:jar :format-json:jar'
    exit 1
}

function Find-GradleJar {
    param(
        [Parameter(Mandatory = $true)][string] $ModuleRelativePath,
        [Parameter(Mandatory = $true)][string] $FilePattern,
        [Parameter(Mandatory = $true)][string] $NameRegex
    )
    $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' }
    $moduleDir = Join-Path (Join-Path $gradleHome 'caches\modules-2\files-2.1') $ModuleRelativePath
    if (-not (Test-Path $moduleDir)) {
        return $null
    }
    Get-ChildItem -Path $moduleDir -Recurse -File -Filter $FilePattern -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match $NameRegex -and $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

$kotlinStdlib = $null
if ($env:KOTLIN_STDLIB_JAR -and (Test-Path $env:KOTLIN_STDLIB_JAR)) {
    $kotlinStdlib = (Resolve-Path $env:KOTLIN_STDLIB_JAR).Path
} else {
    $kotlinStdlib = Find-GradleJar 'org.jetbrains.kotlin\kotlin-stdlib' 'kotlin-stdlib-*.jar' '^kotlin-stdlib-\d.*\.jar$'
}

$aliasFor = $null
if ($env:ALIAS_FOR_JAR -and (Test-Path $env:ALIAS_FOR_JAR)) {
    $aliasFor = (Resolve-Path $env:ALIAS_FOR_JAR).Path
} else {
    $aliasFor = Find-GradleJar 'com.mingliqiye.utils.aliasfor\alias-for' 'alias-for-*.jar' '^alias-for-\d.*\.jar$'
}

$mingliException = $null
if ($env:MINGLI_EXCEPTION_JAR -and (Test-Path $env:MINGLI_EXCEPTION_JAR)) {
    $mingliException = (Resolve-Path $env:MINGLI_EXCEPTION_JAR).Path
} else {
    $mingliException = Find-GradleJar 'com.mingliqiye.exception\mingli-exception' 'mingli-exception-*.jar' '^mingli-exception-\d.*\.jar$'
}

if (-not $kotlinStdlib) {
    Write-Host 'Cannot find kotlin-stdlib jar: set KOTLIN_STDLIB_JAR to kotlin-stdlib-2.x.jar'
    exit 1
}
if (-not $aliasFor) {
    Write-Host 'Cannot find alias-for jar: set ALIAS_FOR_JAR to alias-for-1.0.3.jar'
    exit 1
}
if (-not $mingliException) {
    Write-Host 'Cannot find mingli-exception jar: set MINGLI_EXCEPTION_JAR to mingli-exception-1.1.1-Final.jar'
    exit 1
}

if (Test-Path $classesDir) {
    Remove-Item -Path $classesDir -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
Copy-Item -Path (Join-Path $scriptDir 'META-INF') -Destination $classesDir -Recurse -Force

$classpath = (@($classesDir) + $libJars + @($kotlinStdlib, $aliasFor, $mingliException)) -join [IO.Path]::PathSeparator

$javac = 'javac'
$java = 'java'
if ($env:JAVA_HOME) {
    $candidateJavac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    $candidateJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidateJavac) {
        $javac = $candidateJavac
        $java = $candidateJava
    }
}

Write-Host "kotlin-stdlib : $kotlinStdlib"
Write-Host "alias-for     : $aliasFor"
Write-Host "mingli-exception : $mingliException"
Write-Host "classes       : $classesDir"
Write-Host ''
Write-Host 'javac ...'
& $javac -encoding UTF-8 --release 17 -cp $classpath -d $classesDir (Join-Path $scriptDir 'NativeSmoke.java')
if ($LASTEXITCODE -ne 0) {
    Write-Host "javac failed, exit code = $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host 'java ...'
& $java -cp $classpath com.mingliqiye.document.graalvm.smoke.NativeSmoke
if ($LASTEXITCODE -ne 0) {
    Write-Host "JVM smoke failed, exit code = $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host ''
Write-Host 'JVM smoke passed (SMOKE OK).'
Write-Host ''
Write-Host 'To build the native image (requires GraalVM for JDK 17+ and native-image on PATH;'
Write-Host 'this library maps beans through java.beans.Introspector, so GraalVM for JDK 22+ is recommended):'
Write-Host "  native-image -cp `"$classpath`" -H:Name=`"$(Join-Path $buildDir 'native-smoke')`" ``"
Write-Host '    com.mingliqiye.document.graalvm.smoke.NativeSmoke'
Write-Host ''
Write-Host 'The smoke metadata lives in classes/META-INF/native-image and is discovered from'
Write-Host 'the classpath; metadata embedded in the serialization jar is also applied.'
