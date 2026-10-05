import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config.dart';
import '../../camera/application/camera_service.dart';
import '../../camera/domain/camera_frame.dart';
import '../../gesture/domain/hand_motion.dart';
import '../../gesture/domain/motion_detection_service.dart';
import '../../hand_tracking/domain/hand_tracking_service.dart';
import '../domain/scroll_service.dart';
import 'tracking_state.dart';

// A plain coordinator is also usable in camera-free lifecycle tests.
class TrackingController {
  TrackingController({
    required this.camera,
    required this.tracker,
    required this.motionDetector,
    required this.scroll,
    required this.config,
    required this.onState,
  });

  final CameraService camera;
  final HandTrackingService tracker;
  final MotionDetectionService motionDetector;
  final ScrollService scroll;
  final TrackingConfig config;
  final void Function(TrackingState) onState;
  final Stopwatch _fpsClock = Stopwatch();
  TrackingState _state = const TrackingState();
  Future<void> _commands = Future.value();
  Future<void>? _inference;
  Duration? _lastAccepted;
  bool _wanted = false;
  bool _foreground = true;
  bool _closed = false;
  int _generation = 0;
  int _completed = 0;
  double _fps = 0;

  bool get _shouldRun => _wanted && _foreground && !_closed;

  Future<void> start() {
    _wanted = true;
    return _enqueue();
  }

  Future<void> stop() {
    _wanted = false;
    _generation++;
    scroll.stop();
    return _enqueue();
  }

  Future<void> setForeground(bool foreground) {
    _foreground = foreground;
    if (!foreground) {
      _generation++;
      scroll.stop();
    }
    return _enqueue();
  }

  Future<void> _enqueue() {
    _commands = _commands.then((_) => _reconcile());
    return _commands;
  }

  void _emit(TrackingState state) {
    _state = state;
    if (!_closed) onState(state);
  }

  Future<void> _reconcile() async {
    if (_shouldRun && _state.running) return;
    _emit(const TrackingState(busy: true));
    try {
      await _release();
      if (!_shouldRun) {
        _emit(const TrackingState(motion: HandMotion(action: HandAction.stop)));
        return;
      }
      await tracker.initialize();
      if (!_shouldRun) {
        await _release();
        _emit(const TrackingState());
        return;
      }
      await camera.initialize();
      if (!_shouldRun) {
        await _release();
        _emit(const TrackingState());
        return;
      }
      _fpsClock
        ..reset()
        ..start();
      _completed = 0;
      _fps = 0;
      _lastAccepted = null;
      _emit(
        const TrackingState(
          running: true,
          motion: HandMotion(action: HandAction.start),
        ),
      );
      await camera.startImageStream(_onFrame);
    } catch (error) {
      _wanted = false;
      final cleanupError = await _releaseSafely();
      _emit(
        TrackingState(
          error:
              '${_message(error)}${cleanupError == null ? '' : '\n$cleanupError'}',
        ),
      );
    }
  }

  void _onFrame(CameraFrame frame) {
    if (!_shouldRun || !_state.running || _inference != null) return;
    final last = _lastAccepted;
    if (last != null && frame.timestamp - last < config.frameInterval) return;
    _lastAccepted = frame.timestamp;
    final generation = _generation;
    // Only one Future/frame may be retained. Busy and throttled frames are dropped.
    _inference = _process(
      frame,
      generation,
    ).whenComplete(() => _inference = null);
  }

  Future<void> _process(CameraFrame frame, int generation) async {
    try {
      final result = await tracker.track(frame);
      if (!_shouldRun || generation != _generation) return;
      final motion = motionDetector.process(result);
      scroll.handle(motion);
      _completed++;
      if (_fpsClock.elapsedMilliseconds >= 1000) {
        _fps = _completed * 1000000 / _fpsClock.elapsedMicroseconds;
        _completed = 0;
        _fpsClock.reset();
      }
      _emit(
        TrackingState(
          running: true,
          result: result,
          motion: motion,
          inferenceFps: _fps,
        ),
      );
    } catch (error) {
      if (!_shouldRun || generation != _generation) return;
      _wanted = false;
      _generation++;
      scroll.stop();
      _emit(TrackingState(error: _message(error)));
      // Queue cleanup without waiting for our own inference Future.
      _commands = _commands.then((_) async {
        final cleanupError = await _releaseSafely();
        if (cleanupError != null) _emit(TrackingState(error: cleanupError));
      });
    }
  }

  Future<void> _release() async {
    _generation++;
    scroll.stop();
    // Each owner gets a cleanup attempt even if another owner fails.
    try {
      await camera.stopImageStream();
    } finally {
      try {
        await camera.dispose();
      } finally {
        try {
          await _inference;
          await tracker.dispose();
        } finally {
          motionDetector.reset();
          _fpsClock.stop();
        }
      }
    }
  }

  Future<String?> _releaseSafely() async {
    try {
      await _release();
      return null;
    } catch (error) {
      return 'Resource cleanup failed: $error';
    }
  }

  Future<void> dispose() {
    _closed = true;
    _wanted = false;
    _generation++;
    scroll.stop();
    return _enqueue();
  }

  String _message(Object error) {
    if (error is CameraException && error.code.startsWith('CameraAccess')) {
      return 'Camera permission denied or restricted. Enable Camera in Android app settings, then retry.';
    }
    return 'Tracking failed: $error';
  }
}

class TrackingNotifier extends Notifier<TrackingState> {
  TrackingNotifier(this.createController);
  final TrackingController Function(Ref, void Function(TrackingState))
  createController;
  late final TrackingController controller;

  @override
  TrackingState build() {
    controller = createController(ref, (next) => state = next);
    ref.onDispose(() => unawaited(controller.dispose()));
    return const TrackingState();
  }
}
