import 'package:camera/camera.dart';
import 'package:flutter/services.dart';

import '../application/camera_service.dart';
import '../domain/camera_frame.dart';

class FrontCameraService implements CameraService {
  final Stopwatch _clock = Stopwatch()..start();
  CameraController? _controller;

  @override
  CameraController? get previewController => _controller;

  @override
  Future<void> initialize() async {
    if (_controller != null) return;
    final cameras = await availableCameras();
    final front = cameras.where(
      (c) => c.lensDirection == CameraLensDirection.front,
    );
    if (front.isEmpty) {
      throw CameraException(
        'NoFrontCamera',
        'No front-facing camera available.',
      );
    }
    final controller = CameraController(
      front.first,
      ResolutionPreset.medium,
      fps: 30,
      enableAudio: false,
      imageFormatGroup: ImageFormatGroup.yuv420,
    );
    try {
      await controller.initialize();
      await controller.lockCaptureOrientation(DeviceOrientation.portraitUp);
      _controller = controller;
    } catch (_) {
      await controller.dispose();
      rethrow;
    }
  }

  @override
  Future<void> startImageStream(void Function(CameraFrame) onFrame) async {
    final controller = _controller;
    if (controller == null) throw StateError('Initialize the camera first.');
    if (controller.value.isStreamingImages) return;
    await controller.startImageStream((image) {
      onFrame(
        CameraFrame(
          width: image.width,
          height: image.height,
          // Front-facing sensor rotation in the locked portrait-up orientation.
          rotationDegrees: controller.description.sensorOrientation,
          timestamp: _clock.elapsed,
          planes: image.planes
              .map(
                (p) => FramePlane(
                  bytes: p.bytes,
                  rowStride: p.bytesPerRow,
                  pixelStride: p.bytesPerPixel ?? 1,
                ),
              )
              .toList(growable: false),
        ),
      );
    });
  }

  @override
  Future<void> stopImageStream() async {
    final controller = _controller;
    if (controller != null && controller.value.isStreamingImages) {
      await controller.stopImageStream();
    }
  }

  @override
  Future<void> dispose() async {
    final controller = _controller;
    _controller = null;
    await controller?.dispose();
  }
}
