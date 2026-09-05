import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/utils/constants.dart';
import 'package:kazumi/bean/settings/settings_detail_scaffold.dart';
import 'package:kazumi/bean/dialog/dialog_helper.dart';
import 'package:kazumi/bean/widget/tv_focusable_surface.dart';
import 'package:kazumi/services/platform/tv_mode.dart';
import 'package:kazumi/bean/widget/tv_focus_navigation.dart';

/// Display group for the shortcut list. Functions missing from every group
/// fall back to a trailing "其他" group so new shortcuts never disappear.
class _ShortcutGroup {
  const _ShortcutGroup(this.title, this.icon, this.functions);

  final String title;
  final IconData icon;
  final List<String> functions;
}

const List<_ShortcutGroup> _shortcutGroups = [
  _ShortcutGroup('播放控制', Icons.play_arrow_rounded,
      ['playorpause', 'forward', 'rewind', 'skip', 'next', 'prev']),
  _ShortcutGroup(
      '音量', Icons.volume_up_rounded, ['volumeup', 'volumedown', 'togglemute']),
  _ShortcutGroup('画面与弹幕', Icons.fullscreen_rounded,
      ['fullscreen', 'exitfullscreen', 'screenshot', 'toggledanmaku']),
  _ShortcutGroup('倍速', Icons.speed_rounded,
      ['speed1', 'speed2', 'speed3', 'speedup', 'speeddown']),
];

class _TvRemoteMapping {
  const _TvRemoteMapping(this.icon, this.title, this.description);

  final IconData icon;
  final String title;
  final String description;
}

class _TvRemoteGroup {
  const _TvRemoteGroup(this.title, this.icon, this.items);

  final String title;
  final IconData icon;
  final List<_TvRemoteMapping> items;
}

const List<_TvRemoteGroup> _tvRemoteGroups = [
  _TvRemoteGroup(
    '首页与导航',
    Icons.home_rounded,
    [
      _TvRemoteMapping(
        Icons.pin_rounded,
        '数字键 0–9',
        '按首页编号定位番剧；停顿 1.8 秒后进入，确认键可立即进入',
      ),
      _TvRemoteMapping(
        Icons.manage_search_rounded,
        '找不到编号',
        '最多额外加载 5 页或等待 10 秒；返回键和切换页面均可中断',
      ),
      _TvRemoteMapping(
        Icons.swap_horiz_rounded,
        '顶部分类',
        '左右键切换分类，焦点停留后刷新下方番剧',
      ),
      _TvRemoteMapping(
        Icons.gamepad_rounded,
        '方向键 / OK / 返回',
        '按钮行和分类首尾循环；节目首列左键返回侧栏，侧栏左键跳到右侧；返回关闭当前层',
      ),
      _TvRemoteMapping(
        Icons.home_outlined,
        '长按返回约 1 秒后松开',
        '直接返回本应用的 LCN 主页；短按保持逐层返回，主页短按两次退出。系统 Home 仍回电视桌面',
      ),
      _TvRemoteMapping(
        Icons.play_arrow_rounded,
        '详情页：播放 / 播放暂停键',
        '优先续播上次的源和进度；无可用历史时打开选源，不自动猜测搜索结果',
      ),
    ],
  ),
  _TvRemoteGroup(
    '播放器固定映射',
    Icons.play_circle_rounded,
    [
      _TvRemoteMapping(
        Icons.radio_button_checked,
        'OK / 上 / 下（控制栏隐藏时）',
        'OK 或下键唤醒并聚焦播放/暂停；上键聚焦选集。唤醒不暂停，再按 OK 才执行当前按钮',
      ),
      _TvRemoteMapping(
        Icons.play_circle_outline_rounded,
        '播放与定位',
        '播放/暂停键；左右键或媒体键快退、快进；频道键切换上下集',
      ),
      _TvRemoteMapping(
        Icons.view_list_rounded,
        '选集',
        'EPG/Guide、Top Menu 或黄色功能键',
      ),
      _TvRemoteMapping(
        Icons.subtitles_rounded,
        '弹幕',
        '字幕/CC、音轨或红色功能键统一映射为弹幕开关',
      ),
      _TvRemoteMapping(
        Icons.favorite_outline_rounded,
        '收藏',
        '收藏键或绿色功能键',
      ),
      _TvRemoteMapping(
        Icons.info_outline_rounded,
        '播放信息',
        'INFO 或蓝色功能键，查看硬解、帧率、缓存和丢帧',
      ),
      _TvRemoteMapping(
        Icons.stop_circle_outlined,
        '停止 / 退出',
        'Stop 或 Exit 退出播放器',
      ),
    ],
  ),
  _TvRemoteGroup(
    '系统按键与自定义边界',
    Icons.settings_remote_rounded,
    [
      _TvRemoteMapping(
        Icons.volume_up_rounded,
        '音量 / 静音',
        '交给 Android TV 处理，以兼容电视、CEC、ARC/eARC 和功放',
      ),
      _TvRemoteMapping(
        Icons.security_rounded,
        '系统保留键',
        '部分电视会先拦截 EPG、音量等按键，应用只能处理系统实际传入的键值',
      ),
      _TvRemoteMapping(
        Icons.keyboard_rounded,
        '自定义按键',
        '右侧页签可修改播放器通用快捷键；上述 TV 兼容映射保持固定，避免设备差异导致失效',
      ),
    ],
  ),
];

class KeyboardSettingsPage extends StatefulWidget {
  const KeyboardSettingsPage({super.key});

  @override
  State<KeyboardSettingsPage> createState() => _KeyboardSettingsPageState();
}

class _KeyboardSettingsPageState extends State<KeyboardSettingsPage> {
  String? listeningFunction;
  int? listeningIndex;

  /// Value the listening slot held before recording started; empty means the
  /// slot was newly added and should be dropped when recording is cancelled.
  String originalValue = '';

  late Map<String, List<String>> shortcuts;

  final FocusNode focusNode = FocusNode();
  final List<FocusNode> _tvSectionFocusNodes = [
    FocusNode(debugLabel: 'TV remote guide section'),
    FocusNode(debugLabel: 'TV custom shortcuts section'),
  ];
  int _tvSection = 0;

  bool get isListening => listeningFunction != null && listeningIndex != null;

  @override
  void initState() {
    super.initState();
    // 根据默认快捷键生成可用快捷键列表，并读取已设置值。
    // 旧版实现可能把 '' / '...' 占位符持久化过，甚至把配置存成空列表
    // （单绑定进入录制后直接退出页面）；界面保证每个功能至少保留一个
    // 真实绑定，读入时清理占位符、空配置恢复默认，有变化则回写
    shortcuts = {};
    for (final key in defaultShortcuts.keys) {
      final stored = GStorage.getStringListSettingByName(
        'shortcut_$key',
        defaultValue: defaultShortcuts[key]!.toList(),
      );
      final keys =
          stored.where((value) => value.isNotEmpty && value != '...').toList();
      var changed = keys.length != stored.length;
      if (keys.isEmpty) {
        keys.addAll(defaultShortcuts[key]!);
        changed = true;
      }
      if (changed) {
        GStorage.putStringListSettingByName('shortcut_$key', keys);
      }
      shortcuts[key] = keys;
    }
  }

  @override
  void dispose() {
    cancelListening();
    focusNode.dispose();
    for (final node in _tvSectionFocusNodes) {
      node.dispose();
    }
    super.dispose();
  }

  /// Restores or removes the pending slot so no '...' placeholder is left
  /// behind when recording moves elsewhere. Pure state mutation: build-path
  /// callers wrap it in setState, dispose calls it bare.
  void cancelListening() {
    if (!isListening) return;
    final func = listeningFunction!;
    final keys = shortcuts[func]!;
    final index = listeningIndex!;
    if (index < keys.length) {
      if (originalValue.isEmpty) {
        keys.removeAt(index);
      } else {
        keys[index] = originalValue;
      }
    }
    GStorage.putStringListSettingByName('shortcut_$func', keys);
    listeningFunction = null;
    listeningIndex = null;
    originalValue = '';
  }

  void beginListening(String func, int index) {
    originalValue = shortcuts[func]![index];
    shortcuts[func]![index] = '...';
    listeningFunction = func;
    listeningIndex = index;

    Future.delayed(const Duration(milliseconds: 50), () {
      if (!mounted) return;
      focusNode.requestFocus();
    });
  }

  bool handleShortcutInput(String rawKey) {
    if (!isListening || rawKey.isEmpty) return false;

    final func = listeningFunction!;
    final index = listeningIndex!;

    // 冲突规避
    for (final entry in shortcuts.entries) {
      final otherFunc = entry.key;
      final otherKeys = entry.value;

      for (int i = 0; i < otherKeys.length; i++) {
        if (otherFunc == func && i == index) continue;
        if (otherKeys[i] == rawKey) {
          final name = shortcutsChineseName[otherFunc] ?? otherFunc;
          KazumiDialog.showToast(message: "按键已被【$name】占用，请重新输入");
          return true;
        }
      }
    }
    setState(() {
      shortcuts[func]![index] = rawKey;
      listeningFunction = null;
      listeningIndex = null;
      originalValue = '';
    });
    GStorage.putStringListSettingByName('shortcut_$func', shortcuts[func]!);

    return true;
  }

  // 新槽位不落盘：录制成功或取消时才持久化，storage 里永远没有占位符
  void onAddKey(String func) {
    setState(() {
      cancelListening();
      final keys = shortcuts[func]!;
      keys.add('');
      beginListening(func, keys.length - 1);
    });
  }

  void onKeyCapTap(String func, int index) {
    if (listeningFunction == func && listeningIndex == index) {
      setState(cancelListening);
      return;
    }
    final keyValue = shortcuts[func]![index];
    setState(() {
      cancelListening();
      // 取消可能移除了同列表中的待录制项，按值重新定位（同一功能内按键唯一）
      final idx = shortcuts[func]!.indexOf(keyValue);
      if (idx >= 0) {
        beginListening(func, idx);
      }
    });
  }

  void onRemoveKey(String func, int index) {
    final keyValue = shortcuts[func]![index];
    setState(() {
      cancelListening();
      final keys = shortcuts[func]!;
      keys.remove(keyValue);
      GStorage.putStringListSettingByName('shortcut_$func', keys);
    });
  }

  void restoreDefaults() {
    setState(() {
      listeningFunction = null;
      listeningIndex = null;
      originalValue = '';
      for (final func in shortcuts.keys) {
        shortcuts[func] = defaultShortcuts[func]?.toList() ?? [];
        GStorage.putStringListSettingByName('shortcut_$func', shortcuts[func]!);
      }
    });
    KazumiDialog.showToast(message: '已恢复默认快捷键');
  }

  List<_ShortcutGroup> get displayGroups {
    final groups = <_ShortcutGroup>[];
    final covered = <String>{};
    for (final group in _shortcutGroups) {
      final funcs = group.functions.where(shortcuts.containsKey).toList();
      covered.addAll(funcs);
      if (funcs.isNotEmpty) {
        groups.add(_ShortcutGroup(group.title, group.icon, funcs));
      }
    }
    final leftovers =
        shortcuts.keys.where((func) => !covered.contains(func)).toList();
    if (leftovers.isNotEmpty) {
      groups.add(_ShortcutGroup('其他', Icons.keyboard_rounded, leftovers));
    }
    return groups;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    final showCustomShortcuts = !TvMode.enabled || _tvSection == 1;
    return SettingsDetailScaffold(
      title: Text(TvMode.enabled ? '遥控器与操作设置' : '操作设置'),
      actions: [
        if (showCustomShortcuts)
          IconButton(
            icon: const Icon(Icons.settings_backup_restore_rounded),
            tooltip: '恢复默认',
            onPressed: restoreDefaults,
          ),
      ],
      body: FocusScope(
        autofocus: true,
        child: Focus(
          focusNode: focusNode,
          canRequestFocus: isListening,
          skipTraversal: true,
          descendantsAreFocusable: true,
          onKeyEvent: (node, event) {
            if (event is! KeyDownEvent) return KeyEventResult.ignored;
            if (!isListening) return KeyEventResult.ignored;

            final rawKey = event.logicalKey.keyLabel.isNotEmpty
                ? event.logicalKey.keyLabel
                : event.logicalKey.debugName ?? '';

            final handled = handleShortcutInput(rawKey);
            return handled ? KeyEventResult.handled : KeyEventResult.ignored;
          },
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (TvMode.enabled) ...[
                _buildTvSectionTabs(),
                const SizedBox(height: 14),
              ],
              if (showCustomShortcuts) ...[
                Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 1000),
                    child: Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(
                        TvMode.enabled
                            ? '选择按键标签，再按下遥控器或键盘上的新按键完成修改'
                            : '点按按键标签，再按下新按键完成修改',
                        style: textTheme.bodySmall?.copyWith(
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
                for (final group in displayGroups)
                  Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 1000),
                      child: _buildGroupCard(group),
                    ),
                  ),
              ] else ...[
                Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 1000),
                    child: Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(
                        'TV 固定映射负责遥控器兼容；播放器通用按键可在“自定义按键”中调整。',
                        style: textTheme.bodySmall?.copyWith(
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ),
                ),
                for (final group in _tvRemoteGroups)
                  Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 1000),
                      child: _buildTvRemoteGroup(group),
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTvSectionTabs() {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    const labels = ['遥控器说明', '自定义按键'];
    const icons = [Icons.settings_remote_rounded, Icons.tune_rounded];

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1000),
        child: Row(
          children: [
            for (var index = 0; index < labels.length; index++) ...[
              if (index > 0) const SizedBox(width: 12),
              Expanded(
                child: TvFocusableSurface(
                  focusNode: _tvSectionFocusNodes[index],
                  autofocus: index == 0,
                  highlighted: _tvSection == index,
                  borderRadius: 22,
                  onFocusChange: (focused) {
                    if (focused && _tvSection != index) {
                      setState(() => _tvSection = index);
                    }
                  },
                  onKeyEvent: (node, event) {
                    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
                      return KeyEventResult.ignored;
                    }
                    if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
                      _tvSectionFocusNodes[
                              tvWrappedIndex(index, -1, labels.length)]
                          .requestFocus();
                      return KeyEventResult.handled;
                    }
                    if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
                      _tvSectionFocusNodes[
                              tvWrappedIndex(index, 1, labels.length)]
                          .requestFocus();
                      return KeyEventResult.handled;
                    }
                    return KeyEventResult.ignored;
                  },
                  onPressed: () => setState(() => _tvSection = index),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 160),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 14,
                    ),
                    decoration: BoxDecoration(
                      color: _tvSection == index
                          ? colorScheme.primaryContainer
                          : colorScheme.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(icons[index]),
                        const SizedBox(width: 10),
                        Text(
                          labels[index],
                          style: textTheme.titleMedium?.copyWith(
                            fontWeight: _tvSection == index
                                ? FontWeight.w800
                                : FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildTvRemoteGroup(_TvRemoteGroup group) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TvFocusableSurface(
        onPressed: () {},
        child: Card(
          elevation: 0,
          margin: EdgeInsets.zero,
          color: colorScheme.surfaceContainerLow,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(24),
          ),
          clipBehavior: Clip.antiAlias,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(
                        color: colorScheme.secondaryContainer,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        group.icon,
                        size: 18,
                        color: colorScheme.onSecondaryContainer,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      group.title,
                      style: textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                for (final item in group.items)
                  ListTile(
                    dense: true,
                    contentPadding: const EdgeInsets.symmetric(horizontal: 4),
                    leading: Icon(item.icon),
                    title: Text(item.title),
                    subtitle: Text(item.description),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildGroupCard(_ShortcutGroup group) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 12),
      color: colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: colorScheme.secondaryContainer,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    group.icon,
                    size: 18,
                    color: colorScheme.onSecondaryContainer,
                  ),
                ),
                const SizedBox(width: 12),
                Text(
                  group.title,
                  style: textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 8),
            for (final func in group.functions) _buildShortcutRow(func),
          ],
        ),
      ),
    );
  }

  Widget _buildShortcutRow(String func) {
    final textTheme = Theme.of(context).textTheme;
    final keys = shortcuts[func]!;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Text(shortcutsChineseName[func] ?? func, style: textTheme.bodyMedium),
          const SizedBox(width: 12),
          Expanded(
            child: Wrap(
              alignment: WrapAlignment.end,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 6,
              runSpacing: 6,
              children: [
                for (int i = 0; i < keys.length; i++)
                  _buildKeyCap(func, keys, i),
                _AddKeyButton(onTap: () => onAddKey(func)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildKeyCap(String func, List<String> keys, int i) {
    final listening = listeningFunction == func && listeningIndex == i;
    // 删除入口只按真实绑定数判定，待录制占位符不算数——
    // 否则单绑定时点「添加」会让原按键出现删除按钮，可被误删成空绑定
    final realCount = keys.where((value) => value != '...').length;
    return _KeyCap(
      label: listening ? '按任意键' : keyAliases[keys[i]] ?? keys[i],
      listening: listening,
      onTap: () => onKeyCapTap(func, i),
      onDelete:
          realCount >= 2 && !listening ? () => onRemoveKey(func, i) : null,
    );
  }
}

/// Tonal keycap pill for one bound key; tap to re-record, tap again to
/// cancel. Shows a close icon when the function has spare bindings.
class _KeyCap extends StatelessWidget {
  const _KeyCap({
    required this.label,
    required this.listening,
    required this.onTap,
    this.onDelete,
  });

  final String label;
  final bool listening;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Material(
      color: listening
          ? colorScheme.primaryContainer
          : colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: textTheme.labelMedium?.copyWith(
                  color: listening
                      ? colorScheme.onPrimaryContainer
                      : colorScheme.onSurface,
                  fontWeight: FontWeight.w600,
                ),
              ),
              if (onDelete != null) ...[
                const SizedBox(width: 4),
                GestureDetector(
                  onTap: onDelete,
                  behavior: HitTestBehavior.opaque,
                  child: Icon(
                    Icons.close_rounded,
                    size: 14,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Outlined pill that appends a new binding slot and starts recording.
class _AddKeyButton extends StatelessWidget {
  const _AddKeyButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(8),
      side: BorderSide(color: colorScheme.outlineVariant),
    );

    return Tooltip(
      message: '添加按键',
      child: Material(
        color: Colors.transparent,
        shape: shape,
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          customBorder: shape,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            child: Icon(
              Icons.add_rounded,
              size: 16,
              color: colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      ),
    );
  }
}
