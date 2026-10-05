import '../../camera/domain/camera_frame.dart';
import 'hand_models.dart';

abstract interface class HandTrackingService {
  Future<void> initialize();
  Future<HandTrackingResult> track(CameraFrame frame);
  Future<void> dispose();
}
