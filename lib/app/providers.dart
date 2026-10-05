import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config.dart';
import '../features/camera/application/camera_service.dart';
import '../features/camera/infrastructure/front_camera_service.dart';
import '../features/gesture/application/windowed_motion_detection_service.dart';
import '../features/gesture/domain/motion_detection_service.dart';
import '../features/hand_tracking/domain/hand_tracking_service.dart';
import '../features/hand_tracking/infrastructure/mediapipe_hand_tracking_service.dart';
import '../features/auto_scroll/application/tracking_controller.dart';
import '../features/auto_scroll/application/tracking_state.dart';
import '../features/auto_scroll/domain/scroll_service.dart';
import '../features/auto_scroll/infrastructure/flutter_scroll_service.dart';

final trackingConfigProvider = Provider((ref) => const TrackingConfig());
final motionConfigProvider = Provider((ref) => const MotionConfig());
final scrollConfigProvider = Provider((ref) => const ScrollConfig());

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
  () => TrackingNotifier(
    (ref, onState) => TrackingController(
      camera: ref.read(cameraServiceProvider),
      tracker: ref.read(handTrackingServiceProvider),
      motionDetector: ref.read(motionDetectionServiceProvider),
      scroll: ref.read(scrollServiceProvider),
      config: ref.read(trackingConfigProvider),
      onState: onState,
    ),
  ),
);
