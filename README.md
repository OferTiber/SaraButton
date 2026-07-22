# Sara Button

Sara Button is an open-source Android accessibility app that turns supported
Shelly BLU remote button presses into direct phone calls. Each physical button
can call a different number, or all buttons can share one number. Detection and
configuration stay on the Android device: the app has no Internet permission,
cloud service, account, analytics, or advertising.

The project is device-neutral. It does not depend on a particular phone model,
manufacturer, mobile carrier, or contact.

## Supported remotes

| Remote | Model | BTHome device ID | Buttons |
| --- | --- | ---: | ---: |
| Shelly BLU RC Button 4 | `SBBT-004CUS` | `0x0007` | 4 |
| Shelly BLU RC Button 4 ZB | `SBBT-104CUS` | `0x0016` | 4 |
| Shelly BLU Button Tough 1 ZB | `SBBT-102C` | `0x0017` | 1 |

The app reads unencrypted BTHome v2 advertisements directly. A Shelly gateway,
Bluetooth pairing, Shelly account, and Shelly Smart Control are not required.

## Android compatibility

The app supports Android 6.0 (API 23) and newer. A compatible device needs:

- Bluetooth Low Energy scanning.
- Android Telecom voice-call support and a configured calling account or SIM.
- Permission to place calls and observe whether a call is already active.
- The background permissions requested by the in-app setup checklist.

Platform behavior differs by Android version:

| Android version | Monitoring behavior and permission model |
| --- | --- |
| 6.0 | Foreground-service callback scan. Monitoring resumes after normal boot. |
| 7.0–7.1 | Foreground-service callback scan with Direct Boot recovery. |
| 8.0–9 | Callback scan plus a process-waking `PendingIntent` recovery scan. Android labels BLE scanning as Location access. |
| 10–11 | The same dual scan path. Android also requires Location access set to **Allow all the time** for BLE monitoring while the app is not visible. Sara Button never derives or stores location. |
| 12 and newer | Uses the Nearby devices Bluetooth permissions and does not request Location access. |
| 13 and newer | Also integrates with Android's per-app language settings. |
| 15 and newer | Applies system-bar insets for enforced edge-to-edge layouts. |

The current build compiles and targets Android API 35. Android 8.0 and newer
receive the strongest process-recovery path because the required
`PendingIntent` BLE scan API was introduced in API 26.

Background execution policies still vary between manufacturers. The app uses
an ongoing `connectedDevice` foreground service, refreshes long-running scans,
holds a bounded and renewable partial wake lock, and registers boot, app-update,
and Bluetooth-state recovery. Users may still need to allow unrestricted
battery use in their device settings.

## Features

- One or four phone-number assignments based on the selected remote.
- Optional routing of all four-button remote buttons to the first number.
- Single, double, or long press as the configured trigger type.
- Packet-level duplicate suppression and a 20-second call safety guard.
- An audible and vibration confirmation before placing a call.
- Automatic discovery and immediate local settings persistence.
- Direct Boot support on Android 7.0 and newer.
- Interface and runtime messages in English, Hebrew, Arabic, Simplified
  Chinese, French, German, Hindi, Japanese, Brazilian Portuguese, Russian, and
  Spanish.
- Automatic use of the device language, with an in-app language override.
- Left-to-right and right-to-left layout support.

## Build

Requirements:

- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.1

Run the same checks used by GitHub Actions:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected Android device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK uses Android's standard local debug key. Distributable builds
should use a private release signing key that is never committed to the
repository.

## Download a release APK

Versioned, release-signed APKs are available from
[GitHub Releases](https://github.com/OferTiber/SaraButton/releases). Download
the APK and its adjacent `.sha256` file, then verify the download before
installing it:

```bash
sha256sum --check SaraButton-2.3.0.apk.sha256
```

On macOS, use `shasum -a 256 SaraButton-2.3.0.apk` and compare the result with
the checksum file. Build provenance can also be verified with GitHub CLI:

```bash
gh attestation verify SaraButton-2.3.0.apk --repo OferTiber/SaraButton
```

Android may ask the user to allow the browser or file manager to install
unknown apps. Disable that permission again after installation if it is not
otherwise needed. Future updates must be signed with the same Sara Button
release key; Android will reject an APK signed by a different key.

GitHub Actions creates a release only for a version tag such as `v2.3.0`. The
tag must match `versionName` in `app/build.gradle`. The workflow tests and lints
the project, builds and verifies the signed APK, generates its checksum and
provenance attestation, and attaches the distributable files to the GitHub
Release. Release signing uses encrypted repository secrets; key files and
passwords must never be added to the repository.

## Configure the app

1. Install and open Sara Button.
2. Follow the setup card to grant Bluetooth, Phone, and Notification access.
   On Android 6–11, Android uses a Location permission for BLE scanning. On
   Android 10–11, also choose **Allow all the time** when directed to the app's
   settings. The app does not read location APIs or retain location data.
3. Allow unrestricted background activity and disable the platform's unused-app
   suspension for Sara Button.
4. Enter at least one phone number, including the international country code
   where possible. On a four-button remote, blank assignments disable those
   buttons. Tough 1 ZB uses only the Button 1 number.
5. With the remote near the Android device, press a button once. Discovery
   starts automatically when no remote is configured.
6. Choose the press type and start monitoring. Keep the ongoing monitoring
   notification enabled.
7. Lock the screen and test every configured button. Confirm that the intended
   number receives the call and that the device's dialer handles the
   speakerphone request as expected.
8. Reboot and test again. On Android 7.0 and newer, also test before the first
   unlock to verify Direct Boot behavior on the target device.

Do not force-stop the app: Android intentionally suppresses its receivers until
the user opens it again. Keep Bluetooth enabled and configure a default voice
calling account when multiple SIMs or calling accounts are present.

## Encrypted remotes

The app detects encrypted BTHome advertisements but cannot decrypt them without
the remote's generated key.

- For either RC Button 4 model, remove and reinsert the battery. Within three
  minutes, hold any button for 30 seconds until the LED turns red.
- For Tough 1 ZB, hold its button for 30 seconds until the factory-reset melody
  plays, then release it. Holding for another 10 seconds cancels the reset.

A factory reset erases existing Shelly and Zigbee configuration. Reopen Sara
Button afterward; discovery starts automatically.

## Privacy and security

- The manifest does not request Internet access.
- Phone numbers, the selected remote address, and settings are stored only in
  app-private device-protected preferences so monitoring can work during Direct
  Boot.
- Android backup and device-transfer extraction are disabled for app data.
- The app contains no network client and uploads no phone number, Bluetooth
  address, call detail, device identifier, or analytics event. A selected
  number is passed to Android Telecom only when placing the requested call.
- Debug logging never includes configured phone numbers. Debug discovery logs
  may include nearby Bluetooth addresses and advertised names in local Logcat.

Before sharing a custom build, review its application ID, signing key, app name,
and any changes to logging or permissions.

## Testing and limitations

Unit tests cover all three supported BTHome device IDs, model-name
classification, four-button and one-button packet shapes, event ordering,
encryption, truncation, and current and legacy hold values. CI builds the debug
APK and runs unit tests and Android lint.

BLE delivery, battery policy, dialer behavior, speakerphone handling, and
background process limits vary across Android implementations. Always perform
end-to-end tests on every intended device and Android version. Encrypted
advertisements are intentionally unsupported, and Android may ignore a
speakerphone request or display a calling-account chooser.

This project is an accessibility aid, not a medical device or guaranteed
emergency system. Keep another tested way to request urgent help.

## Project structure

- `MainActivity.java` — setup, permissions, language selection, and discovery.
- `MonitoringService.java` — foreground monitoring and scan recovery.
- `BleScanManager.java` and `BleScanReceiver.java` — shared BLE scan paths.
- `BthomeParser.java` — strict BTHome v2 event parsing.
- `ShellyButtonDevice.java` — supported model and button-count classification.
- `ButtonEventProcessor.java` — event validation, routing, and deduplication.
- `CaregiverCaller.java` — Android Telecom call placement and call guard.
- `ConfirmationSound.java` — audible, vibration, and notification confirmation.
- `AppPreferences.java` — local configuration and Direct Boot storage.
- `AppLanguage.java` — system-language behavior and the locale override.

All paths above are under
`app/src/main/java/com/ofertiber/sarabutton/`.

Forks that publish a separate app should change the Gradle `namespace` and
`applicationId`, Java package declarations and directory, internal action
strings, app name, and signing configuration.

## References

- [Shelly BLU RC Button 4 ZB documentation](https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/)
- [Shelly BLU Button Tough 1 ZB documentation](https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button1_ZB/)
- [Shelly BLU RC Button 4 documentation](https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU/wall_us/)
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android background BLE guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [Android Direct Boot guidance](https://developer.android.com/privacy-and-security/direct-boot)
- [Android Telecom call API](https://developer.android.com/reference/android/telecom/TelecomManager#placeCall(android.net.Uri,%20android.os.Bundle))

## Contributing

Issues and pull requests are welcome. Include the Android version, device
manufacturer/model, remote model, relevant Logcat output with phone numbers and
Bluetooth addresses removed, and clear reproduction steps. Run the Gradle test,
lint, and build command before submitting code.

## License

Sara Button is available under the [MIT License](LICENSE). Shelly product names
and trademarks belong to their respective owners. This independent project is
not affiliated with or endorsed by Shelly Group.
