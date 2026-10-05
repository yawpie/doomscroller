import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';

import '../../../core/config.dart';
import '../../gesture/domain/hand_motion.dart';
import '../domain/scroll_service.dart';

class FlutterScrollService implements ScrollService {
  FlutterScrollService(this.config) {
    _ticker = Ticker(_tick);
  }

  final ScrollConfig config;
  final ScrollController controller = ScrollController();
  late final Ticker _ticker;
  final Stopwatch _freshness = Stopwatch();
  Duration _previousTick = Duration.zero;
  double _pixelsPerSecond = 0;
  bool _disposed = false;

  @override
  void handle(HandMotion motion) {
    if (_disposed) return;
    final moving =
        motion.action == HandAction.moveUp ||
        motion.action == HandAction.moveDown;
    if (!moving || !motion.verticalVelocity.isFinite) {
      stop();
      return;
    }
    final speed =
        (motion.verticalVelocity.abs() * config.pixelsPerNormalizedUnit).clamp(
          0.0,
          config.maximumPixelsPerSecond,
        );
    _pixelsPerSecond = motion.action == HandAction.moveUp ? -speed : speed;
    _freshness
      ..reset()
      ..start();
    if (!_ticker.isActive) {
      _previousTick = Duration.zero;
      _ticker.start();
    }
  }

  void _tick(Duration elapsed) {
    final dt = (elapsed - _previousTick).inMicroseconds / 1000000;
    _previousTick = elapsed;
    if (_freshness.elapsed > config.staleAfter) {
      stop();
      return;
    }
    if (!controller.hasClients) return;
    final position = controller.position;
    final target = (position.pixels + _pixelsPerSecond * dt.clamp(0.0, 0.05))
        .clamp(position.minScrollExtent, position.maxScrollExtent);
    controller.jumpTo(target);
  }

  @override
  void stop() {
    if (_disposed) return;
    _pixelsPerSecond = 0;
    _freshness.stop();
    _ticker.stop();
  }

  @override
  void dispose() {
    if (_disposed) return;
    stop();
    _disposed = true;
    _ticker.dispose();
    controller.dispose();
  }
}
