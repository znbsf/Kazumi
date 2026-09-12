import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/bean/widget/episode_tile.dart';
import 'package:kazumi/modules/roads/road_module.dart';
import 'package:kazumi/pages/video/episode_selection_panel.dart';
import 'package:kazumi/services/platform/tv_mode.dart';

void main() {
  setUp(() => TvMode.setEnabledForTesting(true));
  tearDown(() => TvMode.setEnabledForTesting(false));

  testWidgets(
      'TV panel locates recycled episode, wraps ragged row and activates exact road',
      (tester) async {
    final key = GlobalKey<EpisodeSelectionPanelState>();
    final selected = <List<int>>[];
    await tester.pumpWidget(MaterialApp(
        home: Scaffold(
            body: SizedBox(
      width: 400,
      height: 460,
      child: EpisodeSelectionPanel(
        key: key,
        title: '离线选集布局验证',
        roads: [
          Road(
              name: '本地',
              data: List.generate(99, (i) => '$i'),
              identifier: List.generate(99, (i) => '第${i + 1}集'))
        ],
        selectedRoad: 0,
        selectedEpisode: 98,
        isOffline: true,
        downloads: const {},
        onEpisodeSelected: (episode, road) => selected.add([episode, road]),
      ),
    ))));
    await tester.pumpAndSettle();
    final reveal = key.currentState!.revealCurrentEpisode();
    await tester.pumpAndSettle();
    await reveal;
    expect(FocusManager.instance.primaryFocus?.debugLabel, 'TV episode 0:98');
    final current = FocusManager.instance.primaryFocus!;
    expect(current.rect.bottom, lessThanOrEqualTo(460));
    for (final key in [
      LogicalKeyboardKey.arrowRight,
      LogicalKeyboardKey.arrowRight
    ]) {
      await tester.sendKeyEvent(key);
      await tester.pumpAndSettle();
    }
    expect(FocusManager.instance.primaryFocus?.debugLabel, 'TV episode 0:97');
    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    await tester.pumpAndSettle();
    expect(selected, [
      [97, 0]
    ]);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    expect(FocusManager.instance.primaryFocus?.debugLabel, 'TV episode 0:1');
    expect(find.byType(EpisodeTile), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
