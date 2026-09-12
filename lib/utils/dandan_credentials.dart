// DanDanPlay API credentials for the client signature flow.
// Release/PR CI injects them via --dart-define=DANDANAPI_APPID / DANDANAPI_KEY.
const Map<String, String> dandanCredentials = {
  'id': String.fromEnvironment('DANDANAPI_APPID'),
  'value': String.fromEnvironment('DANDANAPI_KEY'),
};

const String dandanCredentialsMissingMessage =
    '当前构建未配置弹弹Play API 凭证，请在构建时注入 DANDANAPI_APPID 和 DANDANAPI_KEY';

class DandanCredentialsMissingException implements Exception {
  const DandanCredentialsMissingException();

  @override
  String toString() => dandanCredentialsMissingMessage;
}

bool hasCompleteDandanCredentials(Map<String, String> credentials) {
  return (credentials['id'] ?? '').trim().isNotEmpty &&
      (credentials['value'] ?? '').trim().isNotEmpty;
}

bool get hasDandanCredentials =>
    hasCompleteDandanCredentials(dandanCredentials);

void ensureDandanCredentials() {
  if (!hasDandanCredentials) {
    throw const DandanCredentialsMissingException();
  }
}
