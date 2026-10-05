import 'package:camera/camera.dart';

import '../domain/camera_frame.dart';

abstract interface class CameraService {
  CameraController? get previewController;
  Future<void> initialize();
  Future<void> startImageStream(void Function(CameraFrame) onFrame);
  Future<void> stopImageStream();
  Future<void> dispose();
}
