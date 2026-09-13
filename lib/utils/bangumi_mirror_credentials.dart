// Bangumi mirror API credentials for the search signature flow.
// Release/PR CI injects them via --dart-define=KAZUMI_APPID / KAZUMI_KEY.
const Map<String, String> bangumiMirrorCredentials = {
  'id': String.fromEnvironment('KAZUMI_APPID'),
  'value': String.fromEnvironment('KAZUMI_KEY'),
};

bool get hasBangumiMirrorCredentials =>
    bangumiMirrorCredentials.values.every((value) => value.trim().isNotEmpty);

bool isBangumiCommentPath(String path) =>
    RegExp(r'^/p1/(subjects|episodes|characters)/\d+/comments$').hasMatch(path);

class MissingBangumiCommentCredentials implements Exception {
  static const message = '测试包未配置评论镜像凭据';
  const MissingBangumiCommentCredentials();
  @override
  String toString() => message;
}
