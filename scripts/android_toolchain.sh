#!/usr/bin/env bash
# Raspberry Pi 5 / Debian 13 Trixie / ARM64 Android toolchain setup.
# Safe defaults: no apt upgrade, no SDK deletion, no Android package purge.
set -Eeuo pipefail
IFS=$'\n\t'
SDK_ROOT="${ANDROID_HOME:-$HOME/android-sdk}"
JAVA_HOME_EXPECTED="/usr/lib/jvm/java-21-openjdk-arm64"
BUILD_DIR=""
log(){ printf '\n[android_toolchain] %s\n' "$*"; }
warn(){ printf '\n[android_toolchain] WARNING: %s\n' "$*" >&2; }
die(){ printf '\n[android_toolchain] ERROR: %s\n' "$*" >&2; exit 1; }
usage(){ cat <<'HELP'
Usage: ./android_toolchain.sh [--sdk-root PATH] [--build PROJECT_DIR] [--help]
Prepares a Debian 13/Trixie ARM64 Raspberry Pi 5 for Android builds.
  --sdk-root PATH  SDK path (default: $ANDROID_HOME or ~/android-sdk)
  --build DIR      Also run ./gradlew :app:assembleDebug in DIR
  --help           Show this help
Run as a normal user with sudo access, not as root.
HELP
}
while (($#)); do
  case "$1" in
    --sdk-root) [[ $# -ge 2 ]] || die '--sdk-root needs a path'; SDK_ROOT="$2"; shift 2 ;;
    --build) [[ $# -ge 2 ]] || die '--build needs a project directory'; BUILD_DIR="$2"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done
[[ $EUID -ne 0 ]] || die 'Run as your normal user; sudo is used only for package installation.'
command -v sudo >/dev/null || die 'sudo is required.'
[[ -r /etc/os-release ]] || die '/etc/os-release missing.'
# shellcheck disable=SC1091
source /etc/os-release
ARCH="$(dpkg --print-architecture)"
log "Detected ${PRETTY_NAME:-unknown OS}; dpkg architecture=$ARCH; kernel=$(uname -m)"
[[ "$ARCH" == arm64 ]] || die "Expected ARM64 (arm64); got $ARCH."
[[ "${ID:-}" == debian && ("${VERSION_ID:-}" == 13 || "${VERSION_CODENAME:-}" == trixie) ]] || warn 'Verified baseline is Debian 13/Trixie; package availability may differ.'

log 'Refreshing package indexes (not upgrading the whole OS).'
sudo apt-get update
log 'Installing native prerequisites, JDK 21 and Debian ADB.'
sudo apt-get install -y ca-certificates curl file git unzip zip python3 openjdk-21-jdk adb
command -v java >/dev/null || die 'Java missing after package installation.'
JAVA_REAL="$(readlink -f "$(command -v java)")"
if [[ -x "$JAVA_HOME_EXPECTED/bin/java" ]]; then JAVA_HOME_VALUE="$JAVA_HOME_EXPECTED"; else JAVA_HOME_VALUE="$(dirname "$(dirname "$JAVA_REAL")")"; warn "Using detected JAVA_HOME=$JAVA_HOME_VALUE"; fi
java -version
javac -version

log 'Checking Box64; not building/replacing it automatically.'
if ! command -v box64 >/dev/null 2>&1; then
  CANDIDATE="$(apt-cache policy box64 2>/dev/null | awk '/Candidate:/ {print $2; exit}')"
  if [[ -n "$CANDIDATE" && "$CANDIDATE" != '(none)' ]]; then sudo apt-get install -y box64; else warn 'No Box64 APT candidate found. Install/configure Box64 from a trusted source before relying on Google x86-64 AAPT2.'; fi
fi
if command -v box64 >/dev/null 2>&1; then box64 --version || warn 'box64 --version failed.'; fi

mkdir -p "$SDK_ROOT"
SDK_ROOT="$(cd "$SDK_ROOT" && pwd)"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  log 'sdkmanager missing; discover and download the current official Google Linux command-line tools archive.'
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  DOWNLOAD_URL="$(python3 - <<'PY'
import re, urllib.request
url='https://developer.android.com/studio#command-line-tools-only'
req=urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0 android-toolchain-setup'})
with urllib.request.urlopen(req, timeout=40) as r:
    html=r.read().decode('utf-8','replace')
matches=re.findall(r'https?://dl\.google\.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip', html)
if not matches:
    matches=re.findall(r'dl\.google\.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip', html)
    matches=['https://'+m for m in matches]
if not matches: raise SystemExit('No official Linux command-line tools ZIP URL found on Google page.')
print(matches[0])
PY
  )" || die 'Could not find the current official command-line tools URL. Install from https://developer.android.com/studio#command-line-tools-only and rerun.'
  curl -fL --retry 3 "$DOWNLOAD_URL" -o "$TMP_DIR/cmdline-tools.zip"
  mkdir -p "$TMP_DIR/unpack" "$SDK_ROOT/cmdline-tools"
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR/unpack"
  [[ -x "$TMP_DIR/unpack/cmdline-tools/bin/sdkmanager" ]] || die 'Unexpected command-line tools archive layout.'
  if [[ -e "$SDK_ROOT/cmdline-tools/latest" ]]; then mv "$SDK_ROOT/cmdline-tools/latest" "$SDK_ROOT/cmdline-tools/latest.backup.$(date +%Y%m%d%H%M%S)"; fi
  mv "$TMP_DIR/unpack/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$TMP_DIR"
  trap - EXIT
fi
[[ -x "$SDKMANAGER" ]] || die "sdkmanager not executable: $SDKMANAGER"

export ANDROID_HOME="$SDK_ROOT" ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
export JAVA_HOME="$JAVA_HOME_VALUE"
export PATH="$JAVA_HOME/bin:$SDK_ROOT/cmdline-tools/latest/bin:/usr/bin:/usr/lib/android-sdk/platform-tools:$SDK_ROOT/platform-tools:$PATH"
mkdir -p "$ANDROID_USER_HOME"
log 'Accepting SDK licenses and installing platform 34, build-tools 34.0.0, and platform-tools.'
yes | "$SDKMANAGER" --licenses >/dev/null || warn 'sdkmanager license acceptance returned non-zero.'
"$SDKMANAGER" --install 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools'

log 'Adding environment block to ~/.bashrc if not already present.'
BASHRC="$HOME/.bashrc"; touch "$BASHRC"
if ! grep -qF '# >>> android_toolchain.sh managed block >>>' "$BASHRC"; then
cat >> "$BASHRC" <<ENVBLOCK

# >>> android_toolchain.sh managed block >>>
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_USER_HOME="\$HOME/.android"
export JAVA_HOME="$JAVA_HOME_VALUE"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:/usr/bin:/usr/lib/android-sdk/platform-tools:\$ANDROID_HOME/platform-tools:\$PATH"
export BOX64_LOG=0
# <<< android_toolchain.sh managed block <<<
ENVBLOCK
else
  warn 'Existing managed block found; leaving ~/.bashrc unchanged.'
fi

log 'Writing conservative Gradle settings only if ~/.gradle/gradle.properties does not exist.'
mkdir -p "$HOME/.gradle"
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
if [[ ! -e "$GRADLE_PROPS" ]]; then
cat > "$GRADLE_PROPS" <<'PROPS'
# Raspberry Pi 5 / 16 GB RAM conservative defaults
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -XX:ParallelGCThreads=4
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.vfs.watch=true
kotlin.incremental=true
kotlin.code.style=official
android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=true
PROPS
else
  warn "$GRADLE_PROPS exists; preserving it."
fi

log 'Checking native Debian ADB (do not prefer SDK-local x86-64 ADB).'
if [[ -x /usr/bin/adb ]]; then /usr/bin/adb version || warn 'Native ADB version command failed.'; /usr/bin/adb devices || warn 'Could not list devices; connect/unlock phone and approve USB debugging.'; else warn '/usr/bin/adb missing.'; fi

log 'Checking installed SDK packages and official SDK AAPT2.'
"$SDKMANAGER" --list_installed 2>/dev/null | grep -E 'platforms;android-34|build-tools;34.0.0|platform-tools' || true
AAPT2="$SDK_ROOT/build-tools/34.0.0/aapt2"
if [[ -x "$AAPT2" ]]; then
  file "$AAPT2" || true
  set +e; "$AAPT2" version; AAPT_STATUS=$?; set -e
  log "AAPT2 version command exit status: $AAPT_STATUS"
  [[ $AAPT_STATUS -eq 0 ]] || warn 'AAPT2 test failed; inspect Box64 and actual Gradle task logs.'
else warn "AAPT2 missing at $AAPT2"; fi

log 'Host / Box64 diagnostics.'
uname -a; cat /etc/os-release; dpkg --print-architecture; readlink -f "$(command -v java)"
if command -v box64 >/dev/null 2>&1; then
  cat /proc/sys/fs/binfmt_misc/status 2>/dev/null || true
  ls -l /proc/sys/fs/binfmt_misc/ 2>/dev/null || true
  ldd --version 2>&1 | head -n 1 || true
  ldd "$AAPT2" 2>&1 || true
  box64 --help 2>&1 | head -40 || true
  dpkg -S /usr/bin/box64 2>/dev/null || true
  apt-cache policy box64 2>/dev/null || true
fi

if [[ -n "$BUILD_DIR" ]]; then
  [[ -d "$BUILD_DIR" ]] || die "Build directory not found: $BUILD_DIR"
  [[ -x "$BUILD_DIR/gradlew" ]] || die "Executable Gradle wrapper missing: $BUILD_DIR/gradlew"
  log "Running debug build in $BUILD_DIR"
  (cd "$BUILD_DIR" && ./gradlew --stop && ./gradlew :app:assembleDebug --no-configuration-cache --stacktrace)
  log "Expected APK if successful: $BUILD_DIR/app/build/outputs/apk/debug/app-debug.apk"
fi

cat <<SUMMARY

Setup stage completed.
SDK root: $SDK_ROOT
JAVA_HOME: $JAVA_HOME
Shell config: $BASHRC
Gradle properties: $GRADLE_PROPS

Open a new shell or run: source ~/.bashrc
Then check: java -version; /usr/bin/adb devices
Important: the script never runs apt upgrade, purges Android packages, deletes SDKs,
or replaces Google's AAPT2. An AAPT2 'nftw' warning alone neither proves success
nor failure; inspect the exit code and first failing Gradle task.
SUMMARY
