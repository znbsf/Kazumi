import 'dart:convert';

import 'package:kazumi/modules/danmaku/danmaku_module.dart';

/// No fixture is included by a normal build. Only the explicit TV preview
/// build script supplies this synthetic, public-safe JSON at compile time.
const tvPreviewDanmakuBuild = bool.fromEnvironment('KAZUMI_TV_DEMO_DANMAKU');
const _fixture = String.fromEnvironment('KAZUMI_TV_DEMO_FIXTURE');

bool shouldUseTvPreviewDanmaku({
  required bool previewBuild,
  required bool television,
  required bool enabled,
  required bool hasCredentials,
  required bool localPlayback,
}) =>
    previewBuild && television && enabled && !hasCredentials && !localPlayback;

List<DanmakuEntry> loadTvPreviewDanmaku() => decodeTvPreviewDanmaku(_fixture);

/// Six sparse comments per minute for up to four hours, so resume/seek can be
/// tested too. Never writes to download caches or the online service.
List<DanmakuEntry> decodeTvPreviewDanmaku(String encoded) {
  if (encoded.isEmpty) return const [];
  final data = jsonDecode(utf8.decode(base64Decode(encoded))) as Map;
  final seed = (data['danmakus'] as List)
      .map(
          (row) => DanmakuEntry.fromJson(Map<String, dynamic>.from(row as Map)))
      .toList();
  if (seed.length > 12 ||
      seed.any((entry) =>
          !entry.message.startsWith('[本地示例]') ||
          !entry.time.isFinite ||
          entry.time < 0 ||
          entry.time >= 60)) {
    throw const FormatException('Invalid TV preview fixture');
  }
  return [
    for (var minute = 0; minute < 240; minute++)
      for (final entry in seed)
        DanmakuEntry(
          message: entry.message,
          time: minute * 60 + entry.time,
          type: entry.type,
          color: entry.color,
          source: 'LocalPreview',
        ),
  ];
}
