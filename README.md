<!--
    SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
    SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
    SPDX-License-Identifier: GPL-3.0-only
-->

# Basic Audio Recorder

<img src="app/images/icon.svg" alt="app icon" width="72" />

![license badge](https://img.shields.io/github/license/PatrykMis/BAR)

BAR is a simple Android audio recording app derived from [BCR](https://github.com/chenxiaolong/BCR), with its microphone quick settings tile concept based on [this pull request from the original author](https://github.com/chenxiaolong/BCR/pull/165).

BAR started from BCR because it has a robust audio recording and encoding pipeline that supports multiple output formats and accounts for many edge cases and failure conditions that other apps may ignore. BAR records from a selectable Android microphone input source. The output files are saved with a `_mic` suffix in the output directory.

### Features

* Supports Android 13 through 17
* Supports output in various formats:
  * OGG/Opus - Lossy, smallest files, default
  * M4A/AAC - Lossy, smaller files
  * FLAC - Lossless, larger files
  * WAV/PCM - Lossless, largest files, least CPU usage
* Supports Android's Storage Access Framework (can record to SD cards, USB devices, etc.)
* Quick settings toggle
* Material You dynamic theming
* No network access permission
* No ads or tracking
* Fully usable with screen readers, using standard Android controls and accessibility-aware workflows.

### Limitations

* BAR records microphone audio, not phone calls.
* During phone calls, Android may mute microphone recording because of platform privacy restrictions.

### Audio format choices

BAR tries to keep format settings practical instead of pretending that every encoder option is
equally useful.

For M4A/AAC, the bitrate range depends on the selected sample rate and channel count. The ranges,
defaults, and AAC-LC/HE-AAC/HE-AAC v2 switch points are based on the Fraunhofer FDK AAC encoder
documentation available in Android's source tree:
[`aac/documentation/aacEncoder.pdf`](https://android.googlesource.com/platform/external/aac/+/refs/heads/main/documentation/aacEncoder.pdf).
BAR therefore offers AAC at 44100 Hz and 48000 Hz, where those tables are defined.

FLAC is handled differently because it is lossless. Its setting is a compression level, not an
audio quality slider: level 0 is fastest and creates larger files, while higher levels spend more
CPU to make smaller files without changing the decoded audio. BAR defaults to the highest level
because modern devices can normally handle it during recording.

OGG/Opus does not offer 44100 Hz in BAR because that sample rate is not available for the Opus path
used here, so the app only shows the supported Opus sample rates.

### Native microphone sample rate

On Android, the sample rate selected in BAR is the rate of PCM audio the app asks Android to deliver.
It is not a command that forces the microphone ADC, DSP, vendor HAL, or active input route to run at
the same hardware clock. Android can successfully open an `AudioRecord` stream at 44100 Hz or 48000
Hz and still resample internally if the active capture path runs at a different rate.

[Android's `AudioFormat` documentation](https://developer.android.com/reference/android/media/AudioFormat)
describes the sample rate as the rate of the audio content, not necessarily the rate at which that
content is physically produced or captured. It also recommends matching `AudioRecord` to the endpoint
sample rate to improve efficiency and avoid lossy sample-rate conversion.

This is different from using a desktop or pro-audio path through ASIO, WASAPI exclusive mode, a
directly configured ALSA device, or similar. Those setups can often switch the actual device clock.
With Android `AudioRecord`, the app receives a client-facing PCM stream managed by Android's audio
policy and the device vendor's audio stack. `AudioRecord.getSampleRate()` can tell you the configured
stream rate, but it does not prove that no conversion happened before the audio reached the app.

BAR exposes sample-rate choices because matching the most likely native capture path removes an
unnecessary conversion stage. In practical listening tests, avoiding Android's resampler can be
audible on some devices, so BAR does not hide this behind a single default.

Android does not expose a public API that definitively reports the physical native rate of the
complete microphone path. `AudioDeviceInfo.getSampleRates()` reports advertised endpoint support, and
`AudioFormat.SAMPLE_RATE_UNSPECIFIED` asks Android for a route-dependent default, but neither value
certifies the ADC, DSP, or HAL clock rate or proves that resampling is absent.

BAR therefore includes an advanced diagnostic screen that estimates the most likely native capture
rate with a buffer-size heuristic. It compares `AudioRecord.getMinBufferSize()` results for 48000 Hz
and 44100 Hz across common input channel configurations. `getMinBufferSize()` is not defined as a
native-rate query, but it is influenced by Android's input-buffer path and HAL, so its values can
provide useful diagnostic evidence. Current BAR testing has consistently matched known and
independently reported device behavior: Pixel 4 has previously been reported as a 44100 Hz device,
and BAR's heuristic classifies it the same way. Pixel 9 Pro and Pixel 8 devices behave like 48000 Hz
devices, while Xiaomi Redmi 7 behaves like a 44100 Hz device.

### Non-features

As the name alludes, BAR intends to be as basic as possible. The project will have succeeded at its goal if the only updates it ever needs are for compatibility with new Android versions. Thus, many potentially useful features will never be implemented, such as:

* Changing the filename format
* Support for old (unsupported) Android versions (support is dropped as soon as maintenance becomes cumbersome)
* Support for direct boot mode (the state before the device is initially unlocked after reboot)

### Usage

1. Releases are not published yet. Once v1.0 is available, download it from the [releases page](https://github.com/PatrykMis/BAR/releases).
    <!-- TODO(v1.0): Replace this with the normal releases-page sentence after publishing - "Download the latest version from the ..." -->
2. Install BAR.
3. Open BAR.
4. Grant the required microphone and notification permissions when prompted.
5. Pick an output directory, recording source, format, bitrate/quality, and sample rate. If no output directory is selected or if the output directory is no longer accessible, then recordings will be saved to `/sdcard/Android/data/com.patrykmis.bar/files`.
6. Add and position the quick settings tile.
7. Enable the tile whenever you want to record. You can also pause the recording from the persistent notification.

BAR will prompt for microphone and notification permissions. They are required for BAR to be able to record in the background.

### Building from source

BAR can be built like most other Android apps using Android Studio or the Gradle command line.

To build the debug APK:

```bash
./gradlew assembleDebug
```

The output file is written to `app/build/outputs/apk/debug/`. The APK will be signed with the default autogenerated debug key.

To create a release build with a specific signing key, copy `keystore.properties.example` to
`keystore.properties` in the project root. Then edit `keystore.properties` with your keystore path,
key alias, and passwords. The local `keystore.properties` file is ignored by git and is only used
for your own build environment.

Then build the release APK:

```bash
./gradlew assembleRelease
```

### Contributing

Bug fixes and translation pull requests are welcome and much appreciated!

If you are interested in implementing a new feature and would like to see it included in BAR, please open an issue to discuss it first. I intend for BAR to be as simple and low-maintenance as possible, so I am not too inclined to add any new features, but I could be convinced otherwise.

### Roadmap

#### v1.1
- Optionally skip Android-muted segments during phone calls.

### License

BAR is licensed under GPLv3. Please see [`LICENSE`](./LICENSE) for the full license text.
