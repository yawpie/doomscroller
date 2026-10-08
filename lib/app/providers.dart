import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config.dart';
import '../features/camera/application/camera_service.dart';
import '../features/camera/infrastructure/front_camera_service.dart';
import '../features/gesture/application/flick_detector.dart';
import '../features/gesture/application/gesture_coordinator.dart';
import '../features/gesture/application/hand_pose_detector.dart';
import '../features/gesture/application/windowed_motion_detection_service.dart';
import '../features/gesture/domain/motion_detection_service.dart';
import '../features/hand_tracking/domain/hand_tracking_service.dart';
import '../features/hand_tracking/infrastructure/mediapipe_hand_tracking_service.dart';
import '../features/auto_scroll/application/tracking_controller.dart';
import '../features/auto_scroll/application/tracking_state.dart';
import '../features/auto_scroll/domain/scroll_service.dart';
import '../features/auto_scroll/infrastructure/flutter_scroll_service.dart';

final trackingConfigProvider = Provider(
  (ref) => const TrackingConfig(
    inferenceFps: 15,
    detectionConfidence: 0.4,
    presenceConfidence: 0.4,
    trackingConfidence: 0.6,
    maximumInferenceDimension: 480,
    lostAfter: Duration(milliseconds: 750),
  ),
);
final motionConfigProvider = Provider((ref) => const MotionDetectionConfig());
final flickConfigProvider = Provider((ref) => const FlickDetectionConfig());
final handPoseConfigProvider =
    NotifierProvider<HandPoseConfigNotifier, HandPoseDetectionConfig>(
      HandPoseConfigNotifier.new,
    );
final gestureModeProvider = NotifierProvider<GestureModeNotifier, GestureMode>(
  GestureModeNotifier.new,
);
final scrollConfigProvider = Provider((ref) => const ScrollConfig());

class HandPoseConfigNotifier extends Notifier<HandPoseDetectionConfig> {
  @override
  HandPoseDetectionConfig build() => const HandPoseDetectionConfig();

  void configureRearm({HandPoseRearmMode? mode, Duration? duration}) {
    if (duration != null && duration <= Duration.zero) {
      throw ArgumentError.value(duration, 'duration', 'Must be positive.');
    }
    state = state.copyWith(rearmMode: mode, stablePoseDuration: duration);
  }
}

class GestureModeNotifier extends Notifier<GestureMode> {
  @override
  GestureMode build() => GestureMode.combined;

  void setMode(GestureMode mode) => state = mode;
}

final cameraServiceProvider = Provider<CameraService>(
  (ref) => FrontCameraService(),
);
final handTrackingServiceProvider = Provider<HandTrackingService>(
  (ref) => MediaPipeHandTrackingService(ref.watch(trackingConfigProvider)),
);
final motionDetectionServiceProvider = Provider<MotionDetectionService>(
  (ref) => WindowedMotionDetectionService(ref.watch(motionConfigProvider)),
);
final flutterScrollServiceProvider = Provider((ref) {
  final service = FlutterScrollService(ref.watch(scrollConfigProvider));
  ref.onDispose(service.dispose);
  return service;
});
final scrollServiceProvider = Provider<ScrollService>(
  (ref) => ref.watch(flutterScrollServiceProvider),
);

final trackingProvider = NotifierProvider<TrackingNotifier, TrackingState>(
  () => TrackingNotifier((ref, onState) {
    final controller = TrackingController(
      camera: ref.read(cameraServiceProvider),
      tracker: ref.read(handTrackingServiceProvider),
      motionDetector: ref.read(motionDetectionServiceProvider),
      flickDetector: FlickDetector(ref.read(flickConfigProvider)),
      poseDetector: HandPoseDetector(ref.read(handPoseConfigProvider)),
      gestureMode: ref.read(gestureModeProvider),
      scroll: ref.read(scrollServiceProvider),
      config: ref.read(trackingConfigProvider),
      onState: onState,
    );
    ref.listen(handPoseConfigProvider, (previous, next) {
      controller.updateGestureSettings(poseConfig: next);
    });
    ref.listen(gestureModeProvider, (previous, next) {
      controller.updateGestureSettings(mode: next);
    });
    return controller;
  }),
);
