import 'dart:math' as math;

import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';

import '../../../core/config.dart';
import '../../gesture/domain/hand_motion.dart';
import '../domain/scroll_service.dart';
import '../application/scroll_velocity_mapper.dart';

class FlutterScrollService implements ScrollService {
  FlutterScrollService(this.config) : mapper = ScrollVelocityMapper(config) {
    _ticker = Ticker(_tick);
  }

  final ScrollConfig config;
  final ScrollVelocityMapper mapper;
  final ValueNotifier<double> velocity = ValueNotifier(0);
  final ScrollController controller = ScrollController();
  late final Ticker _ticker;
  final Stopwatch _freshness = Stopwatch();
  Duration _previousTick = Duration.zero;
  double _pixelsPerSecond = 0;
  double _targetVelocity = 0;
  bool _disposed = false;

  @override
  void handle(HandMotion motion) {
    if (_disposed) return;
    final target = mapper.map(motion);
    if (target == 0) {
      stop();
      return;
    }
    if (_targetVelocity.sign != target.sign) _pixelsPerSecond = 0;
    _targetVelocity = target;
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
    final step = dt.clamp(0.0, 0.05);
    // Match the configured 60 Hz EMA response even on a different display rate.
    final alpha = 1 - math.pow(1 - config.smoothingAlpha, step * 60);
    _pixelsPerSecond += alpha * (_targetVelocity - _pixelsPerSecond);
    velocity.value = _pixelsPerSecond;
    final position = controller.position;
    final target = (position.pixels + _pixelsPerSecond * step).clamp(
      position.minScrollExtent,
      position.maxScrollExtent,
    );
    controller.jumpTo(target);
  }

  void configure({double? sensitivity, bool? inverted}) {
    mapper.configure(sensitivity: sensitivity, inverted: inverted);
    // Do not keep integrating an old direction/gain until the next inference.
    stop();
  }

  @override
  void stop() {
    if (_disposed) return;
    _pixelsPerSecond = 0;
    _targetVelocity = 0;
    velocity.value = 0;
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
    velocity.dispose();
  }
}
