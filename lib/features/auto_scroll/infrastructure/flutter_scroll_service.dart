import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';

import '../../../core/config.dart';
import '../../gesture/domain/hand_motion.dart';
import '../../gesture/domain/flick_event.dart';
import '../domain/scroll_service.dart';
import '../application/scroll_velocity_mapper.dart';

class FlutterScrollService implements ScrollService {
  FlutterScrollService(this.config) : mapper = ScrollVelocityMapper(config) {
    if (config.flickDuration <= Duration.zero ||
        !config.flickScrollDistance.isFinite ||
        config.flickScrollDistance <= 0) {
      throw ArgumentError(
        'Flick distance and animation duration must be positive.',
      );
    }
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
  bool _flickAnimating = false;
  int _animationGeneration = 0;

  @override
  void handle(HandMotion motion) {
    if (_disposed) return;
    final target = mapper.map(motion);
    if (target == 0) {
      if (motion.action == HandAction.stop) {
        stop();
      } else {
        // Suppressed inference samples must not cancel a discrete flick.
        _stopContinuous();
      }
      return;
    }
    _cancelFlick();
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

  @override
  void handleFlick(FlickEvent event) {
    if (_disposed) return;
    stop();
    if (!controller.hasClients) return;
    final position = controller.position;
    final distance = config.flickAction == FlickScrollAction.page
        ? position.viewportDimension
        : config.flickScrollDistance;
    final direction =
        (event.direction == FlickDirection.up ? -1 : 1) *
        (mapper.inverted ? -1 : 1);
    final target = (position.pixels + distance * direction).clamp(
      position.minScrollExtent,
      position.maxScrollExtent,
    );
    if (target == position.pixels) return;
    final generation = ++_animationGeneration;
    _flickAnimating = true;
    unawaited(
      controller
          .animateTo(
            target,
            duration: config.flickDuration,
            curve: Curves.easeOutCubic,
          )
          .whenComplete(() {
            if (generation == _animationGeneration) _flickAnimating = false;
          }),
    );
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
    _stopContinuous();
    _cancelFlick();
  }

  void _cancelFlick() {
    if (!_flickAnimating) return;
    _animationGeneration++;
    _flickAnimating = false;
    if (controller.hasClients) controller.jumpTo(controller.position.pixels);
  }

  void _stopContinuous() {
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
