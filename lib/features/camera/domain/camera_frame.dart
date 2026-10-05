import 'dart:typed_data';

class FramePlane {
  const FramePlane({
    required this.bytes,
    required this.rowStride,
    required this.pixelStride,
  });

  final Uint8List bytes;
  final int rowStride;
  final int pixelStride;
}

class CameraFrame {
  const CameraFrame({
    required this.width,
    required this.height,
    required this.rotationDegrees,
    required this.timestamp,
    required this.planes,
  });

  final int width;
  final int height;
  final int rotationDegrees;
  final Duration timestamp;
  final List<FramePlane> planes;
}
