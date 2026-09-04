import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/pages/player/controller/player_diagnostics.dart';

void main() {
  test('reports the active MediaCodec path separately from configuration', () {
    final snapshot = PlayerDiagnosticsSnapshot.fromProperties(
      {
        'hwdec-current': 'mediacodec',
        'hwdec-interop': 'mediacodec',
        'current-vo': 'gpu-next',
        'current-gpu-context': 'android',
        'video-params/pixelformat': 'nv12',
        'video-params/hw-pixelformat': 'mediacodec',
        'estimated-vf-fps': '23.976',
        'demuxer-cache-duration': '12.5',
        'frame-drop-count': '0',
        'decoder-frame-drop-count': '1',
        'track-list': '''
          [
            {"type":"video","selected":true,"codec":"h264","codec-profile":"High"},
            {"type":"audio","selected":"yes","codec":"aac"}
          ]
        ''',
      },
      hardwareAccelerationEnabled: true,
      configuredHardwareDecoder: 'auto-safe',
    );

    expect(snapshot.decodeRouteSummary, contains('MediaCodec 通路'));
    expect(snapshot.decodeRouteSummary, contains('配置 auto-safe'));
    expect(snapshot.outputSummary, 'gpu-next · android');
    expect(snapshot.streamSummary, contains('h264 High'));
    expect(snapshot.streamSummary, contains('23.98 fps'));
    expect(snapshot.playbackHealthSummary, contains('解码丢帧 1'));
    expect(snapshot.audioCodec, 'aac');
  });

  test('formats the MediaCodec copy route for TV users', () {
    final snapshot = PlayerDiagnosticsSnapshot.fromProperties(
      const {'hwdec-current': 'mediacodec-copy'},
      hardwareAccelerationEnabled: true,
      configuredHardwareDecoder: 'auto-safe',
    );

    expect(snapshot.decodeRouteSummary, contains('MediaCodec (copy)'));
  });

  test('reports software fallback without claiming hardware decode', () {
    final snapshot = PlayerDiagnosticsSnapshot.fromProperties(
      const {'hwdec-current': 'no'},
      hardwareAccelerationEnabled: true,
      configuredHardwareDecoder: 'auto-safe',
    );

    expect(snapshot.decodeRouteSummary, contains('软件解码'));
    expect(snapshot.decodeRouteSummary, contains('回退'));
  });
}
