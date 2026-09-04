import 'dart:convert';

final class PlayerDiagnosticsSnapshot {
  const PlayerDiagnosticsSnapshot({
    required this.hardwareAccelerationEnabled,
    required this.configuredHardwareDecoder,
    required this.activeHardwareDecoder,
    required this.hardwareInterop,
    required this.videoOutput,
    required this.gpuContext,
    required this.videoCodec,
    required this.videoProfile,
    required this.audioCodec,
    required this.pixelFormat,
    required this.hardwarePixelFormat,
    required this.estimatedFps,
    required this.cacheDuration,
    required this.outputDroppedFrames,
    required this.decoderDroppedFrames,
  });

  factory PlayerDiagnosticsSnapshot.fromProperties(
    Map<String, String> properties, {
    required bool hardwareAccelerationEnabled,
    required String configuredHardwareDecoder,
  }) {
    final tracks = _selectedTrackCodecs(properties['track-list'] ?? '');
    return PlayerDiagnosticsSnapshot(
      hardwareAccelerationEnabled: hardwareAccelerationEnabled,
      configuredHardwareDecoder: configuredHardwareDecoder,
      activeHardwareDecoder: properties['hwdec-current'] ?? '',
      hardwareInterop: properties['hwdec-interop'] ?? '',
      videoOutput: properties['current-vo'] ?? '',
      gpuContext: properties['current-gpu-context'] ?? '',
      videoCodec: tracks.videoCodec,
      videoProfile: tracks.videoProfile,
      audioCodec: tracks.audioCodec,
      pixelFormat: properties['video-params/pixelformat'] ?? '',
      hardwarePixelFormat: properties['video-params/hw-pixelformat'] ?? '',
      estimatedFps: properties['estimated-vf-fps'] ?? '',
      cacheDuration: properties['demuxer-cache-duration'] ?? '',
      outputDroppedFrames: properties['frame-drop-count'] ?? '',
      decoderDroppedFrames: properties['decoder-frame-drop-count'] ?? '',
    );
  }

  final bool hardwareAccelerationEnabled;
  final String configuredHardwareDecoder;
  final String activeHardwareDecoder;
  final String hardwareInterop;
  final String videoOutput;
  final String gpuContext;
  final String videoCodec;
  final String videoProfile;
  final String audioCodec;
  final String pixelFormat;
  final String hardwarePixelFormat;
  final String estimatedFps;
  final String cacheDuration;
  final String outputDroppedFrames;
  final String decoderDroppedFrames;

  String get decodeRouteSummary {
    if (!hardwareAccelerationEnabled || configuredHardwareDecoder == 'no') {
      return '软件解码 · 硬解已关闭';
    }
    if (activeHardwareDecoder.isEmpty) {
      return '正在检测 · 配置 $configuredHardwareDecoder';
    }
    if (activeHardwareDecoder == 'no') {
      return '软件解码 · 已从 $configuredHardwareDecoder 回退';
    }
    final decoder = switch (activeHardwareDecoder) {
      'mediacodec' => 'MediaCodec',
      'mediacodec-copy' => 'MediaCodec (copy)',
      _ => activeHardwareDecoder,
    };
    final interop = hardwareInterop.isEmpty ? '' : ' · $hardwareInterop';
    return '$decoder 通路$interop · 配置 $configuredHardwareDecoder';
  }

  String get outputSummary {
    final values = [videoOutput, gpuContext]
        .where((value) => value.isNotEmpty)
        .toList(growable: false);
    return values.isEmpty ? '暂无数据' : values.join(' · ');
  }

  String get streamSummary {
    final codec =
        [videoCodec, videoProfile].where((value) => value.isNotEmpty).join(' ');
    final pixel = hardwarePixelFormat.isNotEmpty
        ? '$pixelFormat / $hardwarePixelFormat'
        : pixelFormat;
    final fps = _formatDecimal(estimatedFps, suffix: ' fps');
    final values = [codec, pixel, fps]
        .where((value) => value.isNotEmpty)
        .toList(growable: false);
    return values.isEmpty ? '暂无数据' : values.join(' · ');
  }

  String get playbackHealthSummary {
    final outputDrops =
        outputDroppedFrames.isEmpty ? '—' : _formatInteger(outputDroppedFrames);
    final decoderDrops = decoderDroppedFrames.isEmpty
        ? '—'
        : _formatInteger(decoderDroppedFrames);
    final cache = _formatDecimal(cacheDuration, suffix: ' 秒');
    return '输出丢帧 $outputDrops · 解码丢帧 $decoderDrops'
        '${cache.isEmpty ? '' : ' · 缓存 $cache'}';
  }

  static String _formatInteger(String value) {
    return int.tryParse(value)?.toString() ?? value;
  }

  static String _formatDecimal(String value, {required String suffix}) {
    final parsed = double.tryParse(value);
    if (parsed == null) return value.isEmpty ? '' : '$value$suffix';
    final formatted = parsed == parsed.roundToDouble()
        ? parsed.toStringAsFixed(0)
        : parsed.toStringAsFixed(2);
    return '$formatted$suffix';
  }
}

({String videoCodec, String videoProfile, String audioCodec})
    _selectedTrackCodecs(String raw) {
  if (raw.isEmpty) {
    return (videoCodec: '', videoProfile: '', audioCodec: '');
  }
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) {
      return (videoCodec: '', videoProfile: '', audioCodec: '');
    }
    String videoCodec = '';
    String videoProfile = '';
    String audioCodec = '';
    for (final item in decoded) {
      if (item is! Map) continue;
      final selected = item['selected'];
      if (selected != true && selected != 'yes') continue;
      final type = item['type']?.toString();
      if (type == 'video') {
        videoCodec = item['codec']?.toString() ?? '';
        videoProfile = item['codec-profile']?.toString() ?? '';
      } else if (type == 'audio') {
        audioCodec = item['codec']?.toString() ?? '';
      }
    }
    return (
      videoCodec: videoCodec,
      videoProfile: videoProfile,
      audioCodec: audioCodec,
    );
  } catch (_) {
    return (videoCodec: '', videoProfile: '', audioCodec: '');
  }
}
