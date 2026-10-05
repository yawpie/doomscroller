import 'dart:async';
import 'dart:typed_data';

import 'package:camera/camera.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/application/tracking_controller.dart';
import 'package:autoscroll_compvis/features/auto_scroll/application/tracking_state.dart';
import 'package:autoscroll_compvis/features/auto_scroll/domain/scroll_service.dart';
import 'package:autoscroll_compvis/features/camera/application/camera_service.dart';
import 'package:autoscroll_compvis/features/camera/domain/camera_frame.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_tracking_service.dart';

class FakeCamera implements CameraService {
  void Function(CameraFrame)? onFrame;
  Completer<void>? initializing;
  Object? error;
  bool disposed = true;
  @override
  CameraController? get previewController => null;
  @override
  Future<void> initialize() async {
    await initializing?.future;
    if (error != null) throw error!;
    disposed = false;
  }

  @override
  Future<void> startImageStream(void Function(CameraFrame) callback) async =>
      onFrame = callback;
  @override
  Future<void> stopImageStream() async => onFrame = null;
  @override
  Future<void> dispose() async => disposed = true;
  void emit(int ms) => onFrame?.call(
    CameraFrame(
      width: 2,
      height: 2,
      rotationDegrees: 0,
      timestamp: Duration(milliseconds: ms),
      planes: [FramePlane(bytes: Uint8List(4), rowStride: 2, pixelStride: 1)],
    ),
  );
}

class FakeTracker implements HandTrackingService {
  Completer<HandTrackingResult>? pending;
  int calls = 0;
  bool disposed = true;
  @override
  Future<void> initialize() async => disposed = false;
  HandPose? pose;
  @override
  Future<HandTrackingResult> track(CameraFrame frame) {
    calls++;
    return pending?.future ??
        Future.value(
          HandTrackingResult(timestamp: frame.timestamp, pose: pose),
        );
  }

  @override
  Future<void> dispose() async => disposed = true;
}

class FakeScroll implements ScrollService {
  final List<HandMotion> motions = [];
  @override
  void handle(HandMotion motion) => motions.add(motion);
  @override
  void stop() {}
  @override
  void dispose() {}
}

Future<void> flush() => Future<void>.delayed(Duration.zero);

void main() {
  late FakeCamera camera;
  late FakeTracker tracker;
  late FakeScroll scroll;
  late TrackingController controller;
  late List<TrackingState> states;
  setUp(() {
    camera = FakeCamera();
    tracker = FakeTracker();
    scroll = FakeScroll();
    states = [];
    controller = TrackingController(
      camera: camera,
      tracker: tracker,
      scroll: scroll,
      motionDetector: WindowedMotionDetectionService(
        const MotionDetectionConfig(),
      ),
      config: const TrackingConfig(),
      onState: states.add,
    );
  });
  tearDown(() => controller.dispose());

  test(
    'busy frames are dropped and completed frames respect FPS limit',
    () async {
      await controller.start();
      tracker.pending = Completer();
      camera.emit(0);
      camera.emit(100);
      camera.emit(200);
      expect(tracker.calls, 1);
      tracker.pending!.complete(
        const HandTrackingResult(timestamp: Duration.zero),
      );
      await flush();
      tracker.pending = null;
      camera.emit(20);
      expect(tracker.calls, 1);
      camera.emit(300);
      await flush();
      expect(tracker.calls, 2);
    },
  );

  test('stop ignores an in-flight result and then closes resources', () async {
    await controller.start();
    tracker.pending = Completer();
    camera.emit(0);
    final stopping = controller.stop();
    tracker.pending!.complete(
      const HandTrackingResult(timestamp: Duration.zero),
    );
    await stopping;
    expect(scroll.motions, isEmpty);
    expect(camera.disposed && tracker.disposed, isTrue);
    expect(states.last.running, isFalse);
  });

  test(
    'background releases resources; foreground resumes only when requested',
    () async {
      await controller.start();
      await controller.setForeground(false);
      expect(camera.disposed && tracker.disposed, isTrue);
      await controller.setForeground(true);
      expect(states.last.running, isTrue);
      await controller.stop();
      await controller.setForeground(false);
      await controller.setForeground(true);
      expect(states.last.running, isFalse);
    },
  );

  test('stop during camera initialization cannot start the stream', () async {
    camera.initializing = Completer();
    final starting = controller.start();
    await flush();
    final stopping = controller.stop();
    camera.initializing!.complete();
    await starting;
    await stopping;
    expect(camera.onFrame, isNull);
    expect(camera.disposed && tracker.disposed, isTrue);
  });

  test('permission denial reports a recoverable error', () async {
    camera.error = CameraException('CameraAccessDenied', 'Denied');
    await controller.start();
    expect(states.last.error, contains('Camera permission'));
    expect(states.last.running, isFalse);
    camera.error = null;
    await controller.start();
    expect(states.last.running, isTrue);
  });

  test('inference error stops processing and releases resources', () async {
    await controller.start();
    tracker.pending = Completer();
    camera.emit(0);
    tracker.pending!.completeError(StateError('Model failed'));
    await flush();
    await flush();
    expect(states.last.error, contains('Model failed'));
    expect(camera.disposed && tracker.disposed, isTrue);
  });

  test(
    'missing-result watchdog clears stale hand without ending the camera session',
    () async {
      await controller.dispose();
      controller = TrackingController(
        camera: camera,
        tracker: tracker,
        scroll: scroll,
        motionDetector: WindowedMotionDetectionService(
          const MotionDetectionConfig(),
        ),
        config: const TrackingConfig(lostAfter: Duration(milliseconds: 5)),
        onState: states.add,
      );
      tracker.pose = HandPose(
        landmarks: List.generate(
          21,
          (i) => HandLandmark(index: i, x: 0.5, y: 0.5, z: 0),
        ),
      );
      await controller.start();
      camera.emit(0);
      await flush();
      expect(states.last.phase, TrackingPhase.tracking);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      expect(states.last.phase, TrackingPhase.lost);
      expect(states.last.result, isNull);
      expect(states.last.running, isTrue);
      expect(states.last.motion.verticalVelocity, 0);
    },
  );

  test(
    'explicit searching, tracking, lost, searching and reacquisition states',
    () async {
      await controller.start();
      expect(states.last.phase, TrackingPhase.searching);
      tracker.pose = HandPose(
        landmarks: List.generate(
          21,
          (i) => HandLandmark(index: i, x: 0.5, y: 0.5, z: 0),
        ),
      );
      camera.emit(0);
      await flush();
      expect(states.last.phase, TrackingPhase.tracking);
      tracker.pose = null;
      camera.emit(100);
      await flush();
      expect(states.last.phase, TrackingPhase.lost);
      expect(scroll.motions.last.action, HandAction.stop);
      camera.emit(900);
      await flush();
      expect(states.last.phase, TrackingPhase.searching);
      tracker.pose = HandPose(
        landmarks: List.generate(
          21,
          (i) => HandLandmark(index: i, x: 0.5, y: 0.2, z: 0),
        ),
      );
      camera.emit(1000);
      await flush();
      expect(states.last.phase, TrackingPhase.tracking);
      expect(scroll.motions.last.action, HandAction.start);
      await controller.stop();
      expect(states.last.phase, TrackingPhase.disabled);
    },
  );
}
