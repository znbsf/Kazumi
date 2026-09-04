import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/pages/player/player_keyboard_shortcuts.dart';

String _label(LogicalKeyboardKey key) =>
    key.keyLabel.isNotEmpty ? key.keyLabel : key.debugName!;

void main() {
  test('TV shortcuts reserve vertical D-pad keys for opening controls', () {
    final source = <String, List<String>>{
      'volumeup': [_label(LogicalKeyboardKey.arrowUp)],
      'volumedown': [_label(LogicalKeyboardKey.arrowDown)],
      'playorpause': [' '],
    };

    final result = withTvRemoteShortcuts(source);

    expect(result['volumeup'],
        isNot(contains(_label(LogicalKeyboardKey.arrowUp))));
    expect(
      result['volumedown'],
      isNot(contains(_label(LogicalKeyboardKey.arrowDown))),
    );
    expect(result['showcontrols'], contains(_label(LogicalKeyboardKey.select)));
    expect(
        result['showcontrols'], contains(_label(LogicalKeyboardKey.arrowUp)));
    expect(
        result['showcontrols'], contains(_label(LogicalKeyboardKey.arrowDown)));
    expect(
      result['playorpause'],
      contains(_label(LogicalKeyboardKey.mediaPlayPause)),
    );

    // The helper must not mutate the persisted user mapping it receives.
    expect(source['volumeup'], contains(_label(LogicalKeyboardKey.arrowUp)));
    expect(source.containsKey('showcontrols'), isFalse);
  });

  testWidgets('TV remote keys dispatch through the player shortcut handler', (
    tester,
  ) async {
    final focusScopeNode = FocusScopeNode();
    addTearDown(focusScopeNode.dispose);
    var showControlsCount = 0;
    var playPauseCount = 0;
    final shortcuts = withTvRemoteShortcuts(<String, List<String>>{
      'showcontrols': const <String>[],
      'playorpause': const <String>[],
    });

    await tester.pumpWidget(
      MaterialApp(
        home: FocusScope(
          node: focusScopeNode,
          child: Column(
            children: [
              PlayerKeyboardShortcuts(
                focusScopeNode: focusScopeNode,
                shortcuts: shortcuts,
                actions: {
                  'showcontrols': () => showControlsCount += 1,
                  'playorpause': () => playPauseCount += 1,
                },
              ),
              const Focus(autofocus: true, child: SizedBox()),
            ],
          ),
        ),
      ),
    );
    await tester.pump();

    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    await tester.sendKeyEvent(LogicalKeyboardKey.mediaPlayPause);
    await tester.pump();

    expect(showControlsCount, 1);
    expect(playPauseCount, 1);
  });
}
