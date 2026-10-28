#!/bin/bash
set -e

# Configuration
ANDROID_HOME="${ANDROID_HOME:-/home/android-sdk}"
SDK_VERSION="34"
BUILD_TOOLS_VERSION="34.0.0"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}Android SDK Installer for Kharcha Tracker     ${NC}"
echo -e "${GREEN}=============================================${NC}"
echo ""

# Check if running as root
if [[ $EUID -ne 0 ]]; then
    echo -e "${YELLOW}Warning: You are not running as root. Some steps may require sudo.${NC}"
    if [[ $EUID -eq 0 ]]; then
        echo "This script requires root access to install Android SDK"
        echo "Please re-run with: sudo $0"
        exit 1
    fi
fi

# Create directories
echo -e "${YELLOW}Creating Android SDK directories...${NC}"
mkdir -p "${ANDROID_HOME}"

# Check if we can install packages via apt
if command -v apt-get >/dev/null 2>&1; then
    echo -e "${YELLOW}Checking for Android SDK packages...${NC}"
    
    # Try to install Android SDK packages
    echo -e "${YELLOW}Installing Android SDK packages (this may take a while)...${NC}"
    apt-get update
    apt-get install -y \
        android-sdk \
        android-sdk-build-tools \
        android-sdk-platform-tools \
        build-essential \
        zip \
        unzip
    
    echo -e "${GREEN}Android SDK packages installed successfully!${NC}"
else
    echo -e "${YELLOW}apt-get not available, will download command-line tools...${NC}"
fi

# Download command-line tools if not already installed
if ! command -v sdkmanager >/dev/null 2>&1; then
    echo -e "${YELLOW}Downloading Android command-line tools...${NC}"
    
    # Try to download from Google
    if curl --version >/dev/null 2>&1; then
        if ! wget --version >/dev/null 2>&1; then
            URL="https://dl.google.com/android/repository/commandlinetools-linux-11472594066-osx_12.0.0.zip"
        else
            URL="https://dl.google.com/android/repository/commandlinetools-linux-latest.zip"
        fi
    elif command -v wget >/dev/null 2>&1; then
        URL="https://dl.google.com/android/repository/commandlinetools-linux-11472594066-osx_12.0.0.zip"
    else
        echo -e "${RED}Error: curl and wget not found, cannot download Android SDK${NC}"
        exit 1
    fi
    
    echo -e "${YELLOW}Downloading from: $URL${NC}"
    
    # Download with curl
    if curl -L -o /tmp/android-tools.zip "$URL" 2>/dev/null; then
        echo -e "${GREEN}Download complete!${NC}"
        unzip -q /tmp/android-tools.zip -d /tmp/
        echo -e "${GREEN}Extraction complete!${NC}"
        # Move cmdline-tools to proper location
        if [[ -d /tmp/cmdline-tools ]]; then
            mv /tmp/cmdline-tools /opt/android-sdk/
        fi
        rm -f /tmp/android-tools.zip
    else
        # Try with wget
        if wget --timeout=120 -O /tmp/android-tools.zip "$URL" 2>/dev/null; then
            echo -e "${GREEN}Download complete!${NC}"
            unzip -q /tmp/android-tools.zip -d /tmp/
            echo -e "${GREEN}Extraction complete!${NC}"
            # Move cmdline-tools to proper location
            if [[ -d /tmp/cmdline-tools ]]; then
                mv /tmp/cmdline-tools /opt/android-sdk/
            fi
            rm -f /tmp/android-tools.zip
        else
            echo -e "${RED}Failed to download Android SDK from Google${NC}"
            echo -e "${YELLOW}Trying alternative download sources...${NC}"
            
            # Try mirror 1
            URL1="http://mirror.services.cloud.google.com/storage/storage.googleapis.com/android/repository/commandlinetools-linux.zip"
            if curl -L -o /tmp/android-tools.zip "$URL1" 2>/dev/null; then
                echo -e "${GREEN}Download complete from mirror 1!${NC}"
                unzip -q /tmp/android-tools.zip -d /tmp/
            else
                # Try mirror 2
                URL2="https://repo.aosprd.com/android/repository/commandlinetools-linux-11472594066-osx_12.0.0.zip"
                if curl -L -o /tmp/android-tools.zip "$URL2" 2>/dev/null; then
                    echo -e "${GREEN}Download complete from mirror 2!${NC}"
                    unzip -q /tmp/android-tools.zip -d /tmp/
                else
                    echo -e "${RED}Failed to download Android SDK from all mirrors${NC}"
                    echo ""
                    echo -e "${RED}Please try one of the following manual installation methods:${NC}"
                    echo ""
                    echo -e "${YELLOW}Method 1: Download from Google manually and place in /opt/android-sdk/cmdline-tools/
cd ~
mkdir -p android-sdk/cmdline-tools/latest
curl -L -o ~/android-sdk/cmdline-tools/latest/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11472594066-osx_12.0.0.zip
unzip -o ~/android-sdk/cmdline-tools/latest/cmdline-tools.zip -d ~/android-sdk/cmdline-tools/latest/
rm ~/android-sdk/cmdline-tools/latest/cmdline-tools.zip
mv ~/android-sdk/cmdline-tools ~/android-sdk/
export ANDROID_HOME=~/android-sdk
mkdir -p ~/android-sdk/licenses
echo \"dummy = 2026-01-01 Dummy license\" > ~/android-sdk/licenses/accept_sdk_license
chmod +x ~/android-sdk/cmdline-tools/latest/sdkmanager
cd /home/pi/Projects/kharcha-tracker-v1.1/android
sed -i \"s|sdk.dir=.*|sdk.dir=${ANDROID_HOME}|\" local.properties
yes | ~/android-sdk/cmdline-tools/latest/sdkmanager --licenses
yes | ~/android-sdk/cmdline-tools/latest/sdkmanager \"platforms;android-${SDK_VERSION}\" \"build-tools;${BUILD_TOOLS_VERSION}\"
                fi
                rm -f /tmp/android-tools.zip
            fi
        fi
        exit 1
    fi
fi

# Move cmdline-tools to the proper location
echo -e "${YELLOW}Setting up Android SDK structure...${NC}"
mkdir -p "${ANDROID_HOME}/cmdline-tools/latest"

# Check if cmdline-tools exists in /tmp or /opt
if [[ -d /tmp/cmdline-tools ]]; then
    cp -r /tmp/cmdline-tools/* "${ANDROID_HOME}/cmdline-tools/latest/"
    rm -rf /tmp/cmdline-tools
elif [[ -d /opt/android-sdk/cmdline-tools/latest ]]; then
    echo -e "${YELLOW}Copying from /opt/android-sdk/cmdline-tools/latest${NC}"
    cp -r /opt/android-sdk/cmdline-tools/latest/* "${ANDROID_HOME}/cmdline-tools/latest/"
    rm -rf /opt/android-sdk/cmdline-tools
elif [[ -f /tmp/cmdline-tools.zip ]]; then
    echo -e "${RED}Error: cmdline-tools.zip found but not extracted${NC}"
    exit 1
fi

echo -e "${GREEN}Android SDK structure set up!${NC}"

# Set up licenses directory
echo -e "${YELLOW}Setting up licenses...${NC}"
mkdir -p "${ANDROID_HOME}/licenses"

# Create license file
cat > "${ANDROID_HOME}/licenses/accept_all" << EOF
# Android SDK licenses
dummy = 2026-01-01
Android SDK License = 2026-01-01
Android SDK Build-Tools ${BUILD_TOOLS_VERSION} = 2026-01-01
Android SDK Platform ${SDK_VERSION} = 2026-01-01
Google Android License = 2026-01-01
EOF

echo -e "${GREEN}Licenses set up!${NC}"

# Update local.properties in the project
echo -e "${YELLOW}Updating local.properties...${NC}"
cd /home/pi/Projects/kharcha-tracker-v1.1/android

# Check if local.properties exists and update it
if [[ -f local.properties ]]; then
    # Check if sdk.dir line exists
    if ! grep -q "^sdk.dir=" local.properties; then
        echo "sdk.dir=${ANDROID_HOME}" >> local.properties
        echo -e "${GREEN}Updated local.properties with sdk.dir${NC}"
    else
        # Update existing sdk.dir line
        sed -i "s|^sdk.dir=.*|sdk.dir=${ANDROID_HOME}|" local.properties
        echo -e "${GREEN}Updated sdk.dir in local.properties${NC}"
    fi
else
    # Create new local.properties
    echo "sdk.dir=${ANDROID_HOME}" > local.properties
    echo -e "${GREEN}Created local.properties${NC}"
fi

# Environment variables
echo -e "${YELLOW}Setting up environment variables...${NC}"

# Export Android SDK environment variables
echo ""
echo -e "${YELLOW}Please add the following to your ~/.bashrc or ~/.bash_profile:${NC}"
echo ""
echo -e "${YELLOW}export ANDROID_HOME=${ANDROID_HOME}${NC}"
echo -e "${YELLOW}export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools${NC}"
echo ""

# Try to source the changes
echo -e "${YELLOW}Attempting to update environment...${NC}"
export ANDROID_HOME="${ANDROID_HOME}"
export PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

# Verify installation
echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}Verification...${NC}"
echo ""

if command -v sdkmanager >/dev/null 2>&1; then
    echo -e "${GREEN}✓ sdkmanager is available${NC}"
else
    echo -e "${RED}✗ sdkmanager not found in PATH${NC}"
fi

# Check for required platforms
echo ""
echo -e "${YELLOW}Checking for required SDK packages...${NC}"

if [[ -d "${ANDROID_HOME}/platforms/android-${SDK_VERSION}" ]]; then
    echo -e "${GREEN}✓ Android Platform ${SDK_VERSION} is installed${NC}"
else
    echo -e "${YELLOW}✗ Android Platform ${SDK_VERSION} is not installed${NC}"
    echo -e "${YELLOW}  Installing now...${NC}"
    if command -v sdkmanager >/dev/null 2>&1; then
        if yes | sdkmanager "platforms;android-${SDK_VERSION}" 2>/dev/null; then
            echo -e "${GREEN}✓ Android Platform ${SDK_VERSION} installed${NC}"
        else
            echo -e "${RED}Failed to install Android Platform ${SDK_VERSION}${NC}"
            echo ""
            echo -e "${YELLOW}Please try installing manually:${NC}"
            echo "  yes | sdkmanager \"platforms;android-${SDK_VERSION}\""
        fi
    else
        echo -e "${RED}Cannot install - sdkmanager not available${NC}"
    fi
fi

if [[ -d "${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}" ]]; then
    echo -e "${GREEN}✓ Build-Tools ${BUILD_TOOLS_VERSION} is installed${NC}"
else
    echo -e "${YELLOW}✗ Build-Tools ${BUILD_TOOLS_VERSION} is not installed${NC}"
    echo -e "${YELLOW}  Installing now...${NC}"
    if command -v sdkmanager >/dev/null 2>&1; then
        if yes | sdkmanager "build-tools;${BUILD_TOOLS_VERSION}" 2>/dev/null; then
            echo -e "${GREEN}✓ Build-Tools ${BUILD_TOOLS_VERSION} installed${NC}"
        else
            echo -e "${RED}Failed to install Build-Tools ${BUILD_TOOLS_VERSION}${NC}"
            echo ""
            echo -e "${YELLOW}Please try installing manually:${NC}"
            echo "  yes | sdkmanager \"build-tools;${BUILD_TOOLS_VERSION}\""
        fi
    else
        echo -e "${RED}Cannot install - sdkmanager not available${NC}"
    fi
fi

echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}Installation Summary${NC}"
echo -e "${GREEN}=============================================${NC}"
echo ""
echo -e "${YELLOW}Android SDK Installation Complete!${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo ""
echo "1. Add to ~/.bashrc:"
echo "   export ANDROID_HOME=${ANDROID_HOME}"
echo "   export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools"
echo ""
echo "2. Source the updated ~/.bashrc:"
echo "   source ~/.bashrc"
echo ""
echo "3. Accept Android SDK licenses:"
echo "   yes | sdkmanager --licenses"
echo ""
echo "4. Update local.properties in the project:"
echo "   (Already done by this script)"
echo ""
echo "5. Install required packages:"
echo "   yes | sdkmanager platforms;android-${SDK_VERSION} build-tools;${BUILD_TOOLS_VERSION}"
echo ""
echo "6. Build the Android project:"
echo "   cd /home/pi/Projects/kharcha-tracker-v1.1/android"
echo "   ./gradlew clean assembleDebug"
echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}Installation ${NC}complete!"

# Display useful information about the SDK
echo ""
echo -e "${YELLOW}Installed packages:${NC}"
if command -v sdkmanager >/dev/null 2>&1; then
    sdkmanager --list 2>/dev/null | grep -E "(Platform ${SDK_VERSION}|Build-Tools ${BUILD_TOOLS_VERSION})" || true
fi

echo ""
echo -e "${GREEN}Android SDK ready for use!${NC}"
