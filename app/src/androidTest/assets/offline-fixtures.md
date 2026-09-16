# Offline adaptive fixtures

Derived from the existing self-generated tracks-fixture.mp4, not third-party footage.
Tool: ffmpeg N-87862-gad56e8057d (local aDrive installation). Video/audio stream copy, first video and first audio only.

Commands from repository root:

```
ffmpeg -y -i app/src/androidTest/assets/tracks-fixture.mp4 -map 0:v:0 -map 0:a:0 -c copy -bsf:v h264_mp4toannexb -hls_time 4 -hls_playlist_type vod -hls_segment_filename app/src/androidTest/assets/offline-hls/seg%02d.ts app/src/androidTest/assets/offline-hls/index.m3u8
ffmpeg -y -i app/src/androidTest/assets/tracks-fixture.mp4 -map 0:v:0 -map 0:a:0 -c copy -f dash -min_seg_duration 4000000 app/src/androidTest/assets/offline-dash/index.mpd
```

HLS output has two segments because cuts follow source keyframes. DASH is static with separate audio/video initialization and media segments. No encryption, alternate variants, or external subtitles.

## AES-128 HLS fixture

`offline-hls-aes` derives from the same self-generated MP4 using stream copy, first video/audio, HLS VOD and 4-second target duration. FFmpeg version is the same as above. Synthetic key.bin contains bytes 00 through 0f; IV is 000102030405060708090a0b0c0d0e0f. This is test data, not a user credential. Key info uses relative URI key.bin and its absolute local file path. Generation adds `-hls_key_info_file artifacts/offline-aes-keyinfo.txt` and changes segment/manifest output directory to offline-hls-aes. This fixture tests identity AES-128, not DRM licensing.
