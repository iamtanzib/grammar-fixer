# Builds GrammarFixer.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs the JDK and Android SDK in %USERPROFILE%\AndroidBuild (set up once).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$root = "$env:USERPROFILE\AndroidBuild"
$bt = "$root\sdk\build-tools\34.0.0"
$jar = "$root\sdk\platforms\android-34\android.jar"
$env:JAVA_HOME = (Get-ChildItem $root -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
function Run($exe) { & $exe @args; if ($LASTEXITCODE) { throw "$exe failed" } }

Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
New-Item -ItemType Directory build\gen, build\obj, build\dex | Out-Null

Run "$bt\aapt2.exe" compile --dir res -o build\res.zip
Run "$bt\aapt2.exe" link -o build\res.apk -I $jar --manifest AndroidManifest.xml --java build\gen `
  --min-sdk-version 23 --target-sdk-version 34 build\res.zip
$java = Get-ChildItem -Recurse src, build\gen -Filter *.java | ForEach-Object FullName
Run javac -nowarn '-Xlint:-options' -source 8 -target 8 -encoding UTF-8 -classpath $jar -d build\obj @java
$classes = Get-ChildItem -Recurse build\obj -Filter *.class | ForEach-Object FullName
Run "$bt\d8.bat" --release --lib $jar --min-api 23 --output build\dex @classes

# Add classes.dex while keeping aapt2's entries (resources.arsc must stay uncompressed).
python -I -c "import zipfile,sys; s=zipfile.ZipFile(sys.argv[1]); d=zipfile.ZipFile(sys.argv[2],'w'); [d.writestr(i, s.read(i), compress_type=i.compress_type) for i in s.infolist()]; d.write(sys.argv[3], 'classes.dex', zipfile.ZIP_DEFLATED); d.close()" build\res.apk build\app.unaligned.apk build\dex\classes.dex
if ($LASTEXITCODE) { throw 'packing failed' }
Run "$bt\zipalign.exe" -p -f 4 build\app.unaligned.apk build\app.aligned.apk

# Release key. Keep grammarfixer.keystore and keystore-password.txt safe: updates must be signed with the same key.
if (-not (Test-Path keystore-password.txt)) { [guid]::NewGuid().ToString('N') | Set-Content -NoNewline keystore-password.txt }
$pw = Get-Content keystore-password.txt -Raw
if (-not (Test-Path grammarfixer.keystore)) {
  Run keytool -genkeypair -keystore grammarfixer.keystore -alias grammarfixer -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass $pw -keypass $pw -dname 'CN=Grammar Fixer' -storetype PKCS12
}
Run "$bt\apksigner.bat" sign --ks grammarfixer.keystore --ks-pass "pass:$pw" --out ..\GrammarFixer.apk build\app.aligned.apk
Run "$bt\apksigner.bat" verify ..\GrammarFixer.apk
Write-Host 'Built GrammarFixer.apk'
