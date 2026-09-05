import 'dart:convert';
import 'dart:io';

import 'package:canvas_danmaku/danmaku_controller.dart';
import 'package:canvas_danmaku/danmaku_screen.dart';
import 'package:canvas_danmaku/models/danmaku_content_item.dart';
import 'package:canvas_danmaku/models/danmaku_option.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/modules/danmaku/danmaku_module.dart';
import 'package:kazumi/pages/player/controller/player_danmaku_controller.dart';

const _fixturePath = 'test/fixtures/danmaku/timeline_sample.json';

Future<List<DanmakuEntry>> _loadFixture() async {
  final decoded = jsonDecode(await File(_fixturePath).readAsString())
      as Map<String, dynamic>;
  final comments = decoded['danmakus'] as List<dynamic>;
  return comments
      .map((comment) =>
          DanmakuEntry.fromJson(Map<String, dynamic>.from(comment as Map)))
      .toList(growable: false);
}

Map<int, List<DanmakuEntry>> _indexBySecond(
  Iterable<DanmakuEntry> comments,
) {
  final indexed = <int, List<DanmakuEntry>>{};
  for (final comment in comments) {
    (indexed[comment.time.toInt()] ??= <DanmakuEntry>[]).add(comment);
  }
  return indexed;
}

List<DanmakuEntry> _emitAt(
  DanmakuTimelineCursor cursor,
  Map<int, List<DanmakuEntry>> indexed,
  int second,
) {
  return [
    for (final pendingSecond in cursor.advance(second))
      ...indexed[pendingSecond] ?? const <DanmakuEntry>[],
  ];
}

DanmakuItemType _canvasType(int type) {
  if (type == 4) return DanmakuItemType.bottom;
  if (type == 5) return DanmakuItemType.top;
  return DanmakuItemType.scroll;
}

void main() {
  test('local fixture matches the Kazumi cached danmaku format', () async {
    final decoded = jsonDecode(await File(_fixturePath).readAsString())
        as Map<String, dynamic>;
    final comments = await _loadFixture();

    expect(decoded['danDanBangumiID'], 0);
    expect(comments, hasLength(13));
    expect(comments.map((comment) => comment.type).toSet(), {1, 4, 5});
    expect(
        comments.every((comment) => comment.source == 'LocalFixture'), isTrue);
    expect(comments.first.message, '[本地测试] 5秒滚动 A');
    expect(comments.first.time, 5.05);
  });

  test('fixture burst is emitted once and preserves top/bottom types',
      () async {
    final indexed = _indexBySecond(await _loadFixture());
    final cursor = DanmakuTimelineCursor();

    final burst = _emitAt(cursor, indexed, 5);
    expect(burst.map((comment) => comment.message), [
      '[本地测试] 5秒滚动 A',
      '[本地测试] 5秒滚动 B',
      '[本地测试] 5秒滚动 C',
    ]);
    expect(_emitAt(cursor, indexed, 5), isEmpty);

    final next = _emitAt(cursor, indexed, 7);
    expect(next.map((comment) => comment.type), [5, 4]);
  });

  test('fixture catches up short gaps without duplicating seconds', () async {
    final indexed = _indexBySecond(await _loadFixture());
    final cursor = DanmakuTimelineCursor();

    expect(_emitAt(cursor, indexed, 8).single.message, '[本地测试] 8秒恢复');
    expect(
      _emitAt(cursor, indexed, 10).map((comment) => comment.message),
      ['[本地测试] 9秒追帧', '[本地测试] 10秒追帧'],
    );
    expect(_emitAt(cursor, indexed, 10), isEmpty);
  });

  test('fixture does not replay intermediate comments after a large seek',
      () async {
    final indexed = _indexBySecond(await _loadFixture());
    final cursor = DanmakuTimelineCursor();

    _emitAt(cursor, indexed, 10);
    final seekResult = _emitAt(cursor, indexed, 20);
    expect(seekResult.single.message, '[本地测试] 20秒跳转目标');
    expect(
      seekResult.any((comment) => comment.message.contains('不应跨大跳转补发')),
      isFalse,
    );

    cursor.reset();
    expect(_emitAt(cursor, indexed, 60).single.message, '[本地测试] 60秒跳转目标');
    expect(_emitAt(cursor, indexed, 61).single.message, '[本地测试] 61秒重新同步');
  });

  testWidgets('fixture reaches the real canvas controller', (tester) async {
    late DanmakuController<void> canvasController;
    await tester.pumpWidget(
      MaterialApp(
        home: SizedBox(
          width: 1280,
          height: 720,
          child: DanmakuScreen<void>(
            createdController: (controller) => canvasController = controller,
            option: const DanmakuOption(
              fontSize: 24,
              duration: 8,
              staticDuration: 4,
            ),
          ),
        ),
      ),
    );

    final fixtureComments = await tester.runAsync(_loadFixture);
    final indexed = _indexBySecond(fixtureComments!);
    final cursor = DanmakuTimelineCursor();
    final comments = <DanmakuEntry>[
      ..._emitAt(cursor, indexed, 5),
      ..._emitAt(cursor, indexed, 7),
    ];
    for (final comment in comments) {
      canvasController.addDanmaku(
        DanmakuContentItem<void>(
          comment.message,
          color: comment.color,
          type: _canvasType(comment.type),
        ),
      );
    }
    await tester.pump(const Duration(milliseconds: 16));

    expect(canvasController.scrollDanmaku, hasLength(3));
    expect(canvasController.staticDanmaku, hasLength(2));
    expect(
      canvasController.staticDanmaku.map((item) => item.content.type).toSet(),
      {DanmakuItemType.top, DanmakuItemType.bottom},
    );
    expect(canvasController.running, isTrue);

    canvasController.pause();
    expect(canvasController.running, isFalse);
    canvasController.resume();
    expect(canvasController.running, isTrue);
    canvasController.clear();
    expect(canvasController.scrollDanmaku, isEmpty);
    expect(canvasController.staticDanmaku, isEmpty);

    await tester.pumpWidget(const SizedBox.shrink());
  });
}
