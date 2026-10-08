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
import 'package:autoscroll_compvis/features/gesture/application/gesture_coordinator.dart';
import 'package:autoscroll_compvis/features/gesture/domain/flick_event.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/gesture/domain/pose_event.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_tracking_service.dart';

import 'support/hand_pose_fixtures.dart';

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
  final List<FlickEvent> flicks = [];
  final List<FlickDirection> pages = [];
  int stops = 0;
  @override
  void handle(HandMotion motion) => motions.add(motion);
  @override
  void handleFlick(FlickEvent event) => flicks.add(event);
  @override
  void scrollByPage(FlickDirection direction) => pages.add(direction);
  @override
  void stop() => stops++;
  @override
  void dispose() {}
}

Future<void> flush() => Future<void>.delayed(Duration.zero);

HandPose poseAt(double y) => HandPose(
  landmarks: List.generate(
    21,
    (i) => HandLandmark(index: i, x: 0.5, y: y, z: 0),
  ),
);

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

  Future<void> frame(int ms, double? y) async {
    tracker.pose = y == null ? null : poseAt(y);
    camera.emit(ms);
    await flush();
  }

  Future<void> poseFrame(int ms, RecognizedHandPose? pose) async {
    tracker.pose = pose == null ? null : handPoseFixture(pose);
    camera.emit(ms);
    await flush();
  }

  Future<void> usePoseOnly({
    TrackingConfig config = const TrackingConfig(),
  }) async {
    await controller.dispose();
    controller = TrackingController(
      camera: camera,
      tracker: tracker,
      scroll: scroll,
      motionDetector: WindowedMotionDetectionService(
        const MotionDetectionConfig(),
      ),
      gestureMode: GestureMode.poseOnly,
      config: config,
      onState: states.add,
    );
    await controller.start();
  }

  for (final (pose, direction) in [
    (RecognizedHandPose.pointingUp, FlickDirection.up),
    (RecognizedHandPose.pointingDown, FlickDirection.down),
  ]) {
    test('controller dispatches one page for held ${pose.name}', () async {
      await controller.start();
      for (var i = 0; i <= 30; i++) {
        await poseFrame(i * 100, pose);
      }
      expect(scroll.pages, [direction]);
      expect(scroll.flicks, isEmpty);
      expect(
        scroll.motions.any(
          (motion) =>
              motion.action == HandAction.moveUp ||
              motion.action == HandAction.moveDown,
        ),
        isFalse,
      );
      expect(states.last.currentPose, pose);
      expect(states.last.posePhase, HandPosePhase.latched);
      expect(states.last.lastPose?.pose, pose);
      expect(
        states.last.lastPose?.timestamp,
        const Duration(milliseconds: 200),
      );
    });
  }

  test(
    'opposite pose and tracking loss cannot release a confirmed action',
    () async {
      await usePoseOnly();
      for (final ms in [0, 100, 200]) {
        await poseFrame(ms, RecognizedHandPose.pointingDown);
      }
      await poseFrame(300, null);
      expect(scroll.motions.last.action, HandAction.stop);
      for (var i = 4; i <= 10; i++) {
        await poseFrame(i * 100, RecognizedHandPose.pointingUp);
      }
      expect(scroll.pages, [FlickDirection.down]);
      expect(states.last.posePhase, HandPosePhase.latched);
      for (final ms in [1100, 1200, 1300]) {
        await poseFrame(ms, RecognizedHandPose.neutral);
      }
      expect(states.last.posePhase, HandPosePhase.ready);
      for (final ms in [1400, 1500, 1600]) {
        await poseFrame(ms, RecognizedHandPose.pointingUp);
      }
      expect(scroll.pages, [FlickDirection.down, FlickDirection.up]);
    },
  );

  test(
    'missing tracking discards a pose candidate and starts confirmation anew',
    () async {
      await usePoseOnly();
      await poseFrame(0, RecognizedHandPose.pointingDown);
      await poseFrame(100, RecognizedHandPose.pointingDown);
      await poseFrame(200, null);
      await poseFrame(300, RecognizedHandPose.pointingDown);
      await poseFrame(400, RecognizedHandPose.pointingDown);
      expect(scroll.pages, isEmpty);
      await poseFrame(500, RecognizedHandPose.pointingDown);
      expect(scroll.pages, [FlickDirection.down]);
    },
  );

  test(
    'duplicate and backward camera timestamps cannot complete a pose',
    () async {
      await usePoseOnly();
      await poseFrame(0, RecognizedHandPose.pointingDown);
      await poseFrame(100, RecognizedHandPose.pointingDown);
      final calls = tracker.calls;
      for (final ms in [100, 50, 100]) {
        await poseFrame(ms, RecognizedHandPose.pointingDown);
      }
      expect(tracker.calls, calls);
      expect(scroll.pages, isEmpty);
      await poseFrame(200, RecognizedHandPose.pointingDown);
      expect(scroll.pages, [FlickDirection.down]);
    },
  );

  test(
    'settings change rejects pending result and clears ownership without restart',
    () async {
      await usePoseOnly();
      await poseFrame(0, RecognizedHandPose.pointingDown);
      await poseFrame(100, RecognizedHandPose.pointingDown);
      tracker.pending = Completer();
      camera.emit(200);
      final stops = scroll.stops;
      controller.updateGestureSettings(mode: GestureMode.poseOnly);
      expect(scroll.stops, greaterThan(stops));
      expect(camera.onFrame, isNotNull);
      expect(states.last.posePhase, HandPosePhase.ready);
      tracker.pending!.complete(
        poseSample(200, RecognizedHandPose.pointingDown),
      );
      await flush();
      expect(scroll.pages, isEmpty);
      tracker.pending = null;
      await poseFrame(300, RecognizedHandPose.pointingUp);
      await poseFrame(400, RecognizedHandPose.pointingUp);
      expect(scroll.pages, isEmpty);
      await poseFrame(500, RecognizedHandPose.pointingUp);
      expect(scroll.pages, [FlickDirection.up]);
    },
  );

  for (final confirmed in [false, true]) {
    test(
      'mismatched result discards candidate and preserves latch (confirmed=$confirmed)',
      () async {
        await usePoseOnly();
        await poseFrame(0, RecognizedHandPose.pointingDown);
        await poseFrame(100, RecognizedHandPose.pointingDown);
        if (confirmed) await poseFrame(200, RecognizedHandPose.pointingDown);
        tracker.pending = Completer();
        final frameTime = confirmed ? 300 : 200;
        camera.emit(frameTime);
        final stops = scroll.stops;
        tracker.pending!.complete(
          poseSample(frameTime - 100, RecognizedHandPose.pointingDown),
        );
        await flush();
        expect(scroll.stops, greaterThan(stops));
        expect(states.last.phase, TrackingPhase.lost);
        expect(states.last.currentPose, RecognizedHandPose.unknown);
        expect(
          states.last.posePhase,
          confirmed ? HandPosePhase.latched : HandPosePhase.ready,
        );
        tracker.pending = null;
        await poseFrame(400, RecognizedHandPose.pointingDown);
        await poseFrame(500, RecognizedHandPose.pointingDown);
        expect(scroll.pages, confirmed ? [FlickDirection.down] : isEmpty);
        await poseFrame(600, RecognizedHandPose.pointingDown);
        expect(scroll.pages, [FlickDirection.down]);
      },
    );
  }

  test(
    'disabled pose configuration cannot dispatch and enabling starts fresh',
    () async {
      await usePoseOnly();
      controller.updateGestureSettings(
        poseConfig: const HandPoseDetectionConfig(enabled: false),
      );
      for (final ms in [0, 100, 200, 300]) {
        await poseFrame(ms, RecognizedHandPose.pointingDown);
      }
      expect(scroll.pages, isEmpty);
      controller.updateGestureSettings(
        poseConfig: const HandPoseDetectionConfig(),
      );
      await poseFrame(400, RecognizedHandPose.pointingDown);
      await poseFrame(500, RecognizedHandPose.pointingDown);
      expect(scroll.pages, isEmpty);
      await poseFrame(600, RecognizedHandPose.pointingDown);
      expect(scroll.pages, [FlickDirection.down]);
    },
  );

  test(
    'background cleanup resets a pose latch for the resumed session',
    () async {
      await usePoseOnly();
      for (final ms in [0, 100, 200]) {
        await poseFrame(ms, RecognizedHandPose.pointingDown);
      }
      await controller.setForeground(false);
      expect(states.last.posePhase, HandPosePhase.ready);
      await controller.setForeground(true);
      for (final ms in [300, 400, 500]) {
        await poseFrame(ms, RecognizedHandPose.pointingDown);
      }
      expect(scroll.pages, [FlickDirection.down, FlickDirection.down]);
    },
  );

  for (final cleanup in ['stop', 'dispose']) {
    test('$cleanup rejects an in-flight pose confirmation', () async {
      await usePoseOnly();
      await poseFrame(0, RecognizedHandPose.pointingDown);
      await poseFrame(100, RecognizedHandPose.pointingDown);
      tracker.pending = Completer();
      camera.emit(200);
      final closing = cleanup == 'stop'
          ? controller.stop()
          : controller.dispose();
      tracker.pending!.complete(
        poseSample(200, RecognizedHandPose.pointingDown),
      );
      await closing;
      expect(scroll.pages, isEmpty);
      expect(camera.disposed && tracker.disposed, isTrue);
      expect(camera.onFrame, isNull);
    });
  }

  testWidgets('watchdog preserves a confirmed pose lock across reacquisition', (
    tester,
  ) async {
    final localCamera = FakeCamera();
    final localTracker = FakeTracker();
    final localScroll = FakeScroll();
    final localStates = <TrackingState>[];
    final localController = TrackingController(
      camera: localCamera,
      tracker: localTracker,
      scroll: localScroll,
      motionDetector: WindowedMotionDetectionService(
        const MotionDetectionConfig(),
      ),
      gestureMode: GestureMode.poseOnly,
      config: const TrackingConfig(),
      onState: localStates.add,
    );
    await localController.start();
    for (final ms in [0, 100, 200]) {
      localTracker.pose = handPoseFixture(RecognizedHandPose.pointingDown);
      localCamera.emit(ms);
      await tester.pump();
    }
    expect(localScroll.pages, [FlickDirection.down]);
    final stops = localScroll.stops;
    await tester.pump(const Duration(milliseconds: 501));
    expect(localStates.last.phase, TrackingPhase.lost);
    expect(localStates.last.posePhase, HandPosePhase.latched);
    expect(localScroll.stops, greaterThan(stops));
    for (final ms in [800, 900, 1000]) {
      localCamera.emit(ms);
      await tester.pump();
    }
    expect(localScroll.pages, [FlickDirection.down]);
    await localController.dispose();
  });

  testWidgets('settings reset retains a watchdog for the running camera', (
    tester,
  ) async {
    final localCamera = FakeCamera();
    final localTracker = FakeTracker();
    final localScroll = FakeScroll();
    final localStates = <TrackingState>[];
    final localController = TrackingController(
      camera: localCamera,
      tracker: localTracker,
      scroll: localScroll,
      motionDetector: WindowedMotionDetectionService(
        const MotionDetectionConfig(),
      ),
      gestureMode: GestureMode.poseOnly,
      config: const TrackingConfig(),
      onState: localStates.add,
    );
    await localController.start();
    localTracker.pose = handPoseFixture(RecognizedHandPose.pointingDown);
    localCamera.emit(0);
    await tester.pump();
    expect(localStates.last.result, isNotNull);
    await tester.pump(const Duration(milliseconds: 300));
    localController.updateGestureSettings(mode: GestureMode.combined);
    final stops = localScroll.stops;
    await tester.pump(const Duration(milliseconds: 499));
    expect(localStates.last.result, isNotNull);
    await tester.pump(const Duration(milliseconds: 2));
    expect(localStates.last.result, isNull);
    expect(localStates.last.phase, TrackingPhase.lost);
    expect(localStates.last.posePhase, HandPosePhase.ready);
    expect(localStates.last.running, isTrue);
    expect(localScroll.stops, greaterThan(stops));
    await localController.dispose();
  });

  for (final direction in [-1.0, 1.0]) {
    test(
      'controller dispatches one ${direction < 0 ? 'up' : 'down'} flick without continuous scroll',
      () async {
        await controller.start();
        for (var i = 0; i < 5; i++) {
          await frame(i * 100, 0.5 + direction * i * 0.1);
        }
        expect(scroll.flicks, hasLength(1));
        expect(
          scroll.flicks.single.direction,
          direction < 0 ? FlickDirection.up : FlickDirection.down,
        );
        expect(
          scroll.motions.any(
            (motion) =>
                motion.action == HandAction.moveUp ||
                motion.action == HandAction.moveDown,
          ),
          isFalse,
        );
      },
    );
  }

  test(
    'loss during flick candidate stops and requires fresh acquisition',
    () async {
      await controller.start();
      await frame(0, 0.5);
      await frame(100, 0.6);
      await frame(200, null);
      expect(scroll.motions.last.action, HandAction.stop);
      await frame(300, 0.8);
      await frame(400, 0.8);
      expect(scroll.flicks, isEmpty);
    },
  );

  test(
    'background reset cannot retain a partial flick into a new session',
    () async {
      await controller.start();
      await frame(0, 0.5);
      await frame(100, 0.6);
      await controller.setForeground(false);
      final stops = scroll.stops;
      await controller.setForeground(true);
      await frame(300, 0.8);
      expect(scroll.flicks, isEmpty);
      await frame(400, 0.7);
      await frame(500, 0.6);
      expect(scroll.flicks, hasLength(1));
      expect(scroll.flicks.single.direction, FlickDirection.up);
      expect(scroll.stops, greaterThanOrEqualTo(stops));
    },
  );

  test(
    'stop ignores an in-flight result that would complete a flick',
    () async {
      await controller.start();
      await frame(0, 0.5);
      await frame(100, 0.6);
      tracker.pending = Completer();
      camera.emit(200);
      final stopping = controller.stop();
      tracker.pending!.complete(
        HandTrackingResult(
          timestamp: const Duration(milliseconds: 200),
          pose: poseAt(0.7),
        ),
      );
      await stopping;
      expect(scroll.flicks, isEmpty);
      expect(states.last.running, isFalse);
    },
  );

  test(
    'watchdog clears a candidate while preserving cooldown after a trigger',
    () async {
      await controller.dispose();
      controller = TrackingController(
        camera: camera,
        tracker: tracker,
        scroll: scroll,
        motionDetector: WindowedMotionDetectionService(
          const MotionDetectionConfig(),
        ),
        config: const TrackingConfig(lostAfter: Duration(milliseconds: 30)),
        onState: states.add,
      );
      await controller.start();
      await frame(0, 0.5);
      await frame(100, 0.6);
      await Future<void>.delayed(const Duration(milliseconds: 60));
      expect(states.last.phase, TrackingPhase.lost);
      await frame(200, 0.7);
      expect(scroll.flicks, isEmpty);
      await frame(300, 0.7);
      await frame(400, 0.7);
      await frame(500, 0.6);
      await frame(600, 0.5);
      expect(scroll.flicks, hasLength(1));
      await Future<void>.delayed(const Duration(milliseconds: 60));
      await frame(700, 0.7);
      await frame(800, 0.6);
      await frame(900, 0.5);
      expect(scroll.flicks, hasLength(1));
    },
  );

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
