# Basic Audio Recorder

![license badge](https://img.shields.io/github/license/PatrykMis/BAR)

BAR is a simple Android audio recording app forked from [BCR](https://github.com/chenxiaolong/BCR) with an addition of [this pull request from the original author](https://github.com/chenxiaolong/BCR/pull/165). This fork has stripped out functionality / code related to call recording and uses the same codebase as BCR.

I've decided to fork because BCR has a robust audio recording/encoding pipeline that supports multiple output formats and accounts for many edge cases and failure conditions that other apps may ignore. It records from Android's MIC audio source (todo: source selector) and passes the audio through the same encoding pipeline as with call recording. The output files are saved with a _mic suffix in the output directory.

### Features

* Supports Android 11 through 13
* Supports output in various formats:
  * OGG/Opus - Lossy, smallest files, default
  * M4A/AAC - Lossy, smaller files
  * FLAC - Lossless, larger files
  * WAV/PCM - Lossless, largest files, least CPU usage
* Supports Android's Storage Access Framework (can record to SD cards, USB devices, etc.)
* Quick settings toggle
* Material You dynamic theming
* No network access permission
* No third party dependencies

### Non-features

As the name alludes, BAR intends to be a basic as possible. The project will have succeeded at its goal if the only updates it ever needs are for compatibility with new Android versions. Thus, many potentially useful features will never be implemented, such as:

* Changing the filename format
* Support for old (unsupported) Android versions (support is dropped as soon as maintenance becomes cumbersome)
* Support for direct boot mode (the state before the device is initially unlocked after reboot)

### Usage

1. Download the latest version from the [todo: releases page](https://github.com/PatrykMis/BAR/releases). To verify the digital signature, see the [verifying digital signatures](#verifying-digital-signatures) section.

2. Install BAR.

3. Open BAR.

4. Pick an output directory. If no output directory is selected or if the output directory is no longer accessible, then recordings will be saved to `/sdcard/Android/data/com.patrykmis.bar/files`.

For the first time, BAR will prompt for microphone, and notification (Android 13+) permissions. They are required for BAR to be able to record in the background.

### Building from source

BAR can be built like most other Android apps using Android Studio or the gradle command line.

To build the debug APK:

```bash
./gradlew assembleDebug
```

The output file is written to `app/build/outputs/apk/debug/`. The APK will be signed with the default autogenerated debug key.

To create a release build with a specific signing key, set up the following environment variables:

```bash
export RELEASE_KEYSTORE=/path/to/keystore.jks
export RELEASE_KEY_ALIAS=alias_name

read -r -s RELEASE_KEYSTORE_PASSPHRASE
read -r -s RELEASE_KEY_PASSPHRASE
export RELEASE_KEYSTORE_PASSPHRASE
export RELEASE_KEY_PASSPHRASE
```

and then build the release APK:

```bash
./gradlew assembleRelease
```

### Contributing

Bug fix and translation pull requests are welcome and much appreciated!

If you are interested in implementing a new feature and would like to see it included in BAR, please open an issue to discuss it first. I intend for BAR to be as simple and low-maintenance as possible, so I am not too inclined to add any new features, but I could be convinced otherwise.

### License

BAR is licensed under GPLv3. Please see [`LICENSE`](./LICENSE) for the full license text.
