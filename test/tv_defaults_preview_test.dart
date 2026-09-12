import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/pages/router.dart';
import 'package:kazumi/services/platform/tv_mode.dart';
import 'package:kazumi/services/player/playback_cache_policy.dart';
import 'package:kazumi/services/player/tv_preview_danmaku.dart';
import 'package:kazumi/services/storage/settings_keys.dart';
import 'package:kazumi/services/update/auto_updater.dart';

void main() {
  const tv = SettingContext(isTelevision: true, compactLayout: true);
  const mobile = SettingContext(compactLayout: true);
  const mib = 1024 * 1024;
  tearDown(() => TvMode.setEnabledForTesting(false));

  test('TV defaults differ without changing mobile or saved preferences', () {
    expect(SettingsKeys.lowMemoryMode.resolveStored(null, tv), isTrue);
    expect(SettingsKeys.lowMemoryMode.resolveStored(false, tv), isFalse);
    expect(SettingsKeys.lowMemoryMode.resolveDefault(mobile), isFalse);
    expect(SettingsKeys.playerControllerLayerDisappearTime.resolveDefault(tv),
        7000);
    expect(
        SettingsKeys.playerControllerLayerDisappearTime.resolveStored(2500, tv),
        2500);
    expect(
        SettingsKeys.playerControllerLayerDisappearTime.resolveDefault(mobile),
        4000);
    expect(SettingsKeys.danmakuFontSize.resolveDefault(tv), 24.0);
    expect(SettingsKeys.danmakuFontSize.resolveDefault(mobile), 16.0);
    expect(SettingsKeys.danmakuArea.resolveDefault(tv), 0.5);
    expect(SettingsKeys.danmakuArea.resolveDefault(mobile), 1.0);
    expect(SettingsKeys.downloadParallelEpisodes.resolveDefault(tv), 1);
    expect(SettingsKeys.downloadParallelSegments.resolveDefault(tv), 2);
    expect(SettingsKeys.brightnessVolumeGesture.resolveDefault(tv), false);
    expect(SettingsKeys.playerDisableAnimations.resolveDefault(tv), true);
    expect(SettingsKeys.autoUpdate.resolveDefault(tv), false);
    expect(SettingsKeys.hAenable.resolveDefault(tv), true);
    expect(SettingsKeys.hardwareDecoder.resolveDefault(tv), 'auto-safe');
    expect(SettingsKeys.defaultSuperResolutionMode.resolveDefault(tv), 1);
  });

  test('TV cache is bounded; metered network and mobile semantics remain', () {
    int size(bool tv, bool low, bool metered, [bool back = false]) =>
        PlaybackCachePolicy.cacheBytes(
            television: tv, lowMemory: low, metered: metered, backward: back) ~/
        mib;
    expect(size(true, true, false), 64);
    expect(size(true, true, false, true), 16);
    expect(size(true, false, false), 256);
    expect(size(true, false, false, true), 64);
    expect(size(true, true, true), 2);
    expect(size(true, false, true, true), 2);
    expect(size(false, true, false), 2);
    expect(size(false, false, false), 1500);
  });

  test('history has a TV-only rail slot and mobile indices stay unchanged', () {
    TvMode.setEnabledForTesting(true);
    expect(menu.indexForPath('/tab/history/'), 1);
    expect(menu.getPath(2), '/timeline');
    expect(menu.getPath(5), '/remote-help');
    TvMode.setEnabledForTesting(false);
    expect(menu.menuList.length, 4);
    expect(menu.getPath(1), '/timeline');
    expect(menu.getPath(3), '/my');
  });

  test('TV never queries upstream mobile updater', () async {
    TvMode.setEnabledForTesting(true);
    expect(await AutoUpdater().checkForUpdates(), isNull);
    await AutoUpdater().autoCheckForUpdates();
  });

  test('preview requires every gate; credentials/local playback take priority',
      () {
    bool enabled(
            {bool build = true,
            bool tv = true,
            bool toggle = true,
            bool credentials = false,
            bool local = false}) =>
        shouldUseTvPreviewDanmaku(
            previewBuild: build,
            television: tv,
            enabled: toggle,
            hasCredentials: credentials,
            localPlayback: local);
    expect(enabled(), isTrue);
    expect(enabled(build: false), isFalse);
    expect(enabled(tv: false), isFalse);
    expect(enabled(toggle: false), isFalse);
    expect(enabled(credentials: true), isFalse);
    expect(enabled(local: true), isFalse);
    expect(tvPreviewDanmakuBuild, isFalse);
    expect(loadTvPreviewDanmaku(), isEmpty);
  });

  test('preview fixture is labeled, bounded and covers late resume', () {
    final encoded = base64Encode(
        File('test/fixtures/danmaku/tv_preview.json').readAsBytesSync());
    final entries = decodeTvPreviewDanmaku(encoded);
    expect(entries.length, 1440);
    expect(entries.every((e) => e.message.startsWith('[本地示例]')), isTrue);
    expect(entries.every((e) => e.source == 'LocalPreview'), isTrue);
    expect(entries.last.time, lessThan(4 * 3600));
    expect(entries.any((e) => e.time == 14 * 60 + 12), isTrue);
    expect(entries.any((e) => e.message.contains('未配置在线弹幕 API 凭证')), isTrue);
    expect(() => decodeTvPreviewDanmaku('not base64!'), throwsFormatException);
    final unlabeled = base64Encode(utf8.encode(jsonEncode({
      'danmakus': [
        {'m': 'not a labeled demo', 'p': '2,1,16777215,LocalPreview'}
      ]
    })));
    expect(() => decodeTvPreviewDanmaku(unlabeled), throwsFormatException);
  });
}
