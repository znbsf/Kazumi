import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:kazumi/pages/player/episode_comments_view.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive.dart';
import 'package:kazumi/request/clients/bangumi_client.dart';
import 'package:kazumi/request/core/dio_factory.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/utils/bangumi_mirror_credentials.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';

class _Paths extends PathProviderPlatform {
  _Paths(this.path);
  final String path;
  @override
  Future<String?> getApplicationSupportPath() async => path;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory temp;
  late RequestOptions request;
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('comment_routing_');
    PathProviderPlatform.instance = _Paths(temp.path);
    Hive.init(temp.path);
    await GStorage.init();
  });
  tearDownAll(() async {
    DioFactory.reset();
    await Hive.close();
    await temp.delete(recursive: true);
  });
  setUp(() async {
    await GStorage.putSetting(SettingsKeys.enableBangumiProxy, true);
    DioFactory.reset();
    // Inspect the request after the real mirror interceptor has routed it.
    DioFactory.apiDio.interceptors.add(InterceptorsWrapper(onRequest: (o, h) {
      request = o;
      h.resolve(Response(requestOptions: o, statusCode: 200, data: []));
    }));
  });
  test('comments use the mirror only when signing credentials are available',
      () async {
    for (final kind in ['subjects', 'episodes', 'characters']) {
      if (!hasBangumiMirrorCredentials) {
        var requests = 0;
        DioFactory.apiDio.interceptors.insert(0,
            InterceptorsWrapper(onRequest: (o, h) {
          requests++;
          h.next(o);
        }));
        await expectLater(
            BangumiClient.instance
                .get('https://next.bgm.tv/p1/$kind/123/comments'),
            throwsA(isA<MissingBangumiCommentCredentials>()));
        expect(requests, 0);
        continue;
      }
      await BangumiClient.instance
          .get('https://next.bgm.tv/p1/$kind/123/comments?limit=20&offset=40');
      expect(request.uri.host,
          hasBangumiMirrorCredentials ? 'api.kazumi.fyi' : 'next.bgm.tv');
      expect(request.uri.queryParameters['offset'], '40');
      expect(request.headers.containsKey('X-Signature'),
          hasBangumiMirrorCredentials);
      expect(
          request.headers.containsKey('X-AppId'), hasBangumiMirrorCredentials);
    }
  });
  test('ordinary metadata still uses the configured mirror', () async {
    await BangumiClient.instance.get('https://api.bgm.tv/v0/subjects/123');
    expect(request.uri.host, 'api.kazumi.fyi');
  });
  test('disabled mirror leaves comments on the official API', () async {
    await GStorage.putSetting(SettingsKeys.enableBangumiProxy, false);
    await BangumiClient.instance
        .get('https://next.bgm.tv/p1/episodes/123/comments');
    expect(request.uri.host, 'next.bgm.tv');
  });
  testWidgets('missing credentials has an explicit notice and no retry',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
        home: Scaffold(
            body: EpisodeCommentsView(
      episode: 10,
      episodeInfo: null,
      comments: const [],
      isLoading: false,
      hasError: false,
      missingCredentials: true,
      isAscending: false,
      onToggleSort: () {},
      onSelectEpisode: () {},
      onRefresh: () {},
    ))));
    expect(find.text(MissingBangumiCommentCredentials.message), findsOneWidget);
    expect(find.text('评论暂时未能加载'), findsNothing);
    expect(
        tester
            .widget<IconButton>(find.byWidgetPredicate(
                (w) => w is IconButton && w.tooltip == '刷新评论'))
            .onPressed,
        isNull);
    expect(tester.takeException(), isNull);
  });
}
