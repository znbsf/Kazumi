import 'package:flutter/material.dart';
import 'package:kazumi/bean/widget/tv_focusable_surface.dart';

class TvRemoteHelpPage extends StatelessWidget {
  const TvRemoteHelpPage({super.key});

  static const _groups = <_RemoteHelpGroup>[
    _RemoteHelpGroup(
      title: '首页选台',
      items: [
        _RemoteHelpItem(
          Icons.pin_rounded,
          '数字键 0–9',
          '按首页从左到右、从上到下的编号定位番剧；停顿 1.8 秒后进入',
        ),
        _RemoteHelpItem(
          Icons.check_circle_outline_rounded,
          '确认键',
          '立即进入当前高亮的编号，无需等待',
        ),
        _RemoteHelpItem(
          Icons.grid_view_rounded,
          '无对应编号',
          '最多加载 5 页或等待 10 秒；到源末尾才提示不存在，超限则提示暂未加载到',
        ),
        _RemoteHelpItem(
          Icons.cancel_outlined,
          '中断查找',
          '按返回或切换到其他页面',
        ),
      ],
    ),
    _RemoteHelpGroup(
      title: '通用导航',
      items: [
        _RemoteHelpItem(
          Icons.gamepad_rounded,
          '方向键 / OK',
          '移动焦点并打开当前项目',
        ),
        _RemoteHelpItem(
          Icons.keyboard_return_rounded,
          '返回',
          '关闭当前页面或面板；主页连按两次退出应用',
        ),
      ],
    ),
    _RemoteHelpGroup(
      title: '播放器',
      items: [
        _RemoteHelpItem(
          Icons.play_circle_outline_rounded,
          '播放/暂停',
          '播放暂停键；隐藏控件时 OK 或上下键唤出控件',
        ),
        _RemoteHelpItem(
          Icons.fast_forward_rounded,
          '快进 / 快退',
          '左右方向键或媒体快进、快退键',
        ),
        _RemoteHelpItem(
          Icons.skip_next_rounded,
          '切集',
          '频道加减或上一集、下一集媒体键',
        ),
        _RemoteHelpItem(
          Icons.view_list_rounded,
          '选集',
          'EPG/Guide、Top Menu 或黄色功能键',
        ),
        _RemoteHelpItem(
          Icons.subtitles_rounded,
          '弹幕',
          '字幕/CC、音轨或红色功能键（统一映射为弹幕）',
        ),
        _RemoteHelpItem(
          Icons.favorite_outline_rounded,
          '收藏',
          '收藏键或绿色功能键',
        ),
        _RemoteHelpItem(
          Icons.info_outline_rounded,
          '播放信息',
          'INFO 或蓝色功能键，查看硬解、帧率、缓存和丢帧',
        ),
        _RemoteHelpItem(
          Icons.stop_circle_outlined,
          '停止 / 退出',
          'Stop 或 Exit 退出播放器',
        ),
      ],
    ),
    _RemoteHelpGroup(
      title: '系统按键',
      items: [
        _RemoteHelpItem(
          Icons.volume_up_rounded,
          '音量 / 静音',
          '交给 Android TV 处理，以兼容电视、CEC、ARC/eARC 和功放',
        ),
        _RemoteHelpItem(
          Icons.settings_remote_rounded,
          '设备差异',
          '某些电视会先拦截 EPG、音量等键，应用只能处理系统实际传入的键值',
        ),
      ],
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('遥控器按键对照表')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(28, 12, 28, 40),
        children: [
          Text(
            '不同品牌的遥控器键名可能不同，下列是 Kazumi TV 的通用映射。',
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 20),
          for (final group in _groups) ...[
            Text(group.title, style: theme.textTheme.headlineSmall),
            const SizedBox(height: 10),
            for (final item in group.items) ...[
              TvFocusableSurface(
                onPressed: () {},
                child: Card(
                  margin: EdgeInsets.zero,
                  child: ListTile(
                    leading: Icon(item.icon),
                    title: Text(item.keyName),
                    subtitle: Text(item.description),
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],
            const SizedBox(height: 16),
          ],
        ],
      ),
    );
  }
}

class _RemoteHelpGroup {
  const _RemoteHelpGroup({required this.title, required this.items});

  final String title;
  final List<_RemoteHelpItem> items;
}

class _RemoteHelpItem {
  const _RemoteHelpItem(this.icon, this.keyName, this.description);

  final IconData icon;
  final String keyName;
  final String description;
}
