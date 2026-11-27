#!/usr/bin/env python3
"""Static validation that works without Android SDK. Not a substitute for Gradle/APK build."""
from pathlib import Path
import sys, xml.etree.ElementTree as ET, zipfile, re
root=Path(__file__).resolve().parent.parent
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml')
ns='{http://schemas.android.com/apk/res/android}'
tools_ns='{http://schemas.android.com/tools}'
declared=manifest.getroot().findall('uses-permission')
perms={x.attrib.get(ns+'name') for x in declared if x.attrib.get(tools_ns+'node') != 'remove'}
removed={x.attrib.get(ns+'name') for x in declared if x.attrib.get(tools_ns+'node') == 'remove'}
assert {'android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION'} <= removed
assert 'android.permission.BLUETOOTH_CONNECT' in perms
assert 'android.permission.BLUETOOTH_SCAN' in perms
assert 'android.permission.INTERNET' not in perms
assert 'android.permission.ACCESS_FINE_LOCATION' not in perms
assert 'android.permission.READ_EXTERNAL_STORAGE' not in perms
assert 'android.permission.WRITE_EXTERNAL_STORAGE' not in perms
main=(root/'app/src/main/java/com/mali2/inspector/MainActivity.java').read_text('utf8')
bridge=(root/'app/src/main/java/com/mali2/inspector/JieliBridge.java').read_text('utf8')
for token in ('getBondedDevices()', 'createRfcommSocketToServiceRecord', 'getInputStream()', 'AncIdentity.read', 'ACTION_CREATE_DOCUMENT', 'BLUETOOTH_CONNECT', 'markGesture', 'rawBytes'):
 assert token in main,token
for token in ('PREFER_SPP', 'setUseDeviceAuth(true)', 'PROTOCOL_TYPE_SPP', 'requestDeviceInfo', 'getDevSysInfo', 'getDeviceSettingsInfo','getAllVoiceModes','getCurrentVoiceMode','onBatteryChange','onVoiceModeList','safeGetters'):
 assert token in bridge,token
for forbidden in ('setCurrentVoiceMode(', 'modifyDeviceSettingsInfo(', 'startOTA(', 'upgradeFirmware(', 'setUseDeviceAuth(false)'):
 assert forbidden not in main+bridge,forbidden
bat=(root/'COMPILAR_APK_WINDOWS.bat').read_bytes()
assert main.count('getOutputStream()') == 1
assert 'AncIdentity.read(socket.getInputStream(), socket.getOutputStream())' in main
demo = (root/'app/src/main/java/com/mali2/inspector/DemoPanel.java').read_text('utf8')
assert 'android.bluetooth' not in demo
assert bat.startswith(b'@echo off\r\n') and not bat.startswith(b'\xef\xbb\xbf')
workflow=(root/'.github/workflows/build-apk.yml').read_text()
assert 'actions/upload-artifact@v4' in workflow
assert 'gradle --no-daemon :app:assembleDebug' in workflow
assert 'gitsha==sha' in workflow
assert (root/'scripts/preparar_sdk_jieli.ps1').exists()
for aar in ['jl_bluetooth_rcsp_V4.2.0_40250-release.aar','jldecryption_v0.4-release.aar']:
 assert aar in workflow and aar in (root/'scripts/preparar_sdk_jieli.ps1').read_text()
print('PASS: permissões reduzidas; integração SPP/RCSP; consulta ANC documentada; demonstração local; sem OTA ou alteração de configuração; BAT sem BOM; build GitHub/Windows; AAR oficiais')
print('NOT TESTED: Android Gradle compilation, user Bluetooth hardware and RCSP authentication')
