// Explicit, emulator-only target. Not included by lib/main.dart.
import 'package:flutter/material.dart';
import 'package:hive_ce/hive.dart';
import 'package:path_provider/path_provider.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/services/platform/tv_mode.dart';
import 'tv_focus_fixtures.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await TvMode.initialize();
  final support = await getApplicationSupportDirectory();
  Hive.init('${support.path}/focus-fixture-only');
  await GStorage.init();
  runApp(FocusFixtureApp());
}
