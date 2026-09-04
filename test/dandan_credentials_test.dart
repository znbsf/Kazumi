import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/utils/dandan_credentials.dart';

void main() {
  test('credentials require both a non-empty id and key', () {
    expect(
      hasCompleteDandanCredentials({'id': 'app', 'value': 'secret'}),
      isTrue,
    );
    expect(
      hasCompleteDandanCredentials({'id': 'app', 'value': ''}),
      isFalse,
    );
    expect(
      hasCompleteDandanCredentials({'id': ' ', 'value': 'secret'}),
      isFalse,
    );
  });

  test('missing local build credentials fail before a network request', () {
    if (hasDandanCredentials) {
      return;
    }
    expect(
      ensureDandanCredentials,
      throwsA(isA<DandanCredentialsMissingException>()),
    );
  });
}
