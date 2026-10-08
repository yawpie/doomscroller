import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config.dart';
import '../../camera/application/camera_service.dart';
import '../../camera/domain/camera_frame.dart';
import '../../gesture/application/flick_detector.dart';
import '../../gesture/application/gesture_coordinator.dart';
import '../../gesture/application/hand_pose_detector.dart';
import '../../gesture/domain/flick_event.dart';
import '../../gesture/domain/hand_motion.dart';
import '../../gesture/domain/motion_detection_service.dart';
import '../../gesture/domain/pose_event.dart';
import '../../hand_tracking/domain/hand_tracking_service.dart';
import '../domain/scroll_service.dart';
import 'tracking_state.dart';

// A plain coordinator is also usable in camera-free lifecycle tests.
class TrackingController {
  TrackingController({
    required this.camera,
    required this.tracker,
    required this.motionDetector,
    FlickDetector? flickDetector,
    HandPoseDetector? poseDetector,
    GestureMode gestureMode = GestureMode.combined,
    required this.scroll,
    required this.config,
    required this.onState,
  }) : _gestures = GestureCoordinator(
         motionDetector: motionDetector,
         flickDetector:
             flickDetector ?? FlickDetector(const FlickDetectionConfig()),
         poseDetector: poseDetector,
         mode: gestureMode,
       );

  final CameraService camera;
  final HandTrackingService tracker;
  final MotionDetectionService motionDetector;
  final GestureCoordinator _gestures;
  final ScrollService scroll;
  final TrackingConfig config;
  final void Function(TrackingState) onState;
  final Stopwatch _fpsClock = Stopwatch();
  TrackingState _state = const TrackingState();
  Future<void> _commands = Future.value();
  Future<void>? _inference;
  Duration? _lastAccepted;
  Duration? _lastResultTimestamp;
  bool _wanted = false;
  bool _foreground = true;
  bool _closed = false;
  int _generation = 0;
  int _completed = 0;
  double _fps = 0;
  Duration? _lastHand;
  Timer? _resultWatchdog;

  bool get _shouldRun => _wanted && _foreground && !_closed;

  void updateGestureSettings({
    HandPoseDetectionConfig? poseConfig,
    GestureMode? mode,
  }) {
    if (_closed) return;
    final detector = poseConfig == null ? null : HandPoseDetector(poseConfig);
    _generation++;
    _resultWatchdog?.cancel();
    scroll.stop();
    _gestures.configure(detector: detector, mode: mode);
    if (_shouldRun && _state.running) _armResultWatchdog(_generation);
    _emit(
      TrackingState(
        running: _state.running,
        busy: _state.busy,
        result: _state.result,
        phase: _state.phase,
        inferenceFps: _state.inferenceFps,
        inferenceMilliseconds: _state.inferenceMilliseconds,
        error: _state.error,
      ),
    );
  }

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
      _lastResultTimestamp = null;
      _lastHand = null;
      _emit(
        const TrackingState(
          running: true,
          phase: TrackingPhase.searching,
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
      final latency = Stopwatch()..start();
      final result = await tracker.track(frame);
      if (!_shouldRun || generation != _generation) return;
      if (latency.elapsed >= config.lostAfter) {
        _resultWatchdog?.cancel();
        _handleTrackingLoss();
        return;
      }
      final previousTimestamp = _lastResultTimestamp;
      if (result.timestamp != frame.timestamp ||
          result.timestamp < Duration.zero ||
          (previousTimestamp != null &&
              result.timestamp <= previousTimestamp)) {
        _handleTrackingLoss();
        return;
      }
      _lastResultTimestamp = result.timestamp;
      final decision = _gestures.process(result);
      final motion = decision.motion;
      final flick = decision.flick;
      final pose = decision.pose;
      if (pose != null) {
        scroll.scrollByPage(
          pose.pose == RecognizedHandPose.pointingUp
              ? FlickDirection.up
              : FlickDirection.down,
        );
      } else if (flick != null) {
        scroll.handleFlick(flick);
      } else {
        scroll.handle(motion);
      }
      _armResultWatchdog(generation);
      if (result.handDetected) _lastHand = result.timestamp;
      final phase = result.handDetected
          ? TrackingPhase.tracking
          : _lastHand != null &&
                result.timestamp - _lastHand! < config.lostAfter
          ? TrackingPhase.lost
          : TrackingPhase.searching;
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
          flick: flick,
          currentPose: _gestures.poseDetector.currentPose,
          posePhase: _gestures.poseDetector.phase,
          lastPose: _gestures.poseDetector.lastEvent,
          confirmationProgress: _gestures.poseDetector.confirmationProgress,
          cooldownRemaining: _gestures.poseDetector.cooldownRemaining,
          inferenceFps: _fps,
          phase: phase,
          inferenceMilliseconds: latency.elapsedMicroseconds / 1000,
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

  void _armResultWatchdog(int generation) {
    _resultWatchdog?.cancel();
    _resultWatchdog = Timer(config.lostAfter, () {
      if (!_shouldRun || generation != _generation) return;
      _handleTrackingLoss();
    });
  }

  void _handleTrackingLoss() {
    scroll.stop();
    _gestures.loseTracking();
    _emit(
      TrackingState(
        running: true,
        phase: _lastHand == null ? TrackingPhase.searching : TrackingPhase.lost,
        currentPose: _gestures.poseDetector.currentPose,
        posePhase: _gestures.poseDetector.phase,
        lastPose: _gestures.poseDetector.lastEvent,
        confirmationProgress: _gestures.poseDetector.confirmationProgress,
        cooldownRemaining: _gestures.poseDetector.cooldownRemaining,
      ),
    );
  }

  Future<void> _release() async {
    _generation++;
    _resultWatchdog?.cancel();
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
          _gestures.reset();
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
    if (error is CameraException) {
      return 'Camera unavailable: ${error.description ?? error.code}. Retry after closing other camera apps.';
    }
    if (error is PlatformException) {
      return 'Hand tracking unavailable: ${error.message ?? error.code}. Stop and retry; check the bundled model and device support.';
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
