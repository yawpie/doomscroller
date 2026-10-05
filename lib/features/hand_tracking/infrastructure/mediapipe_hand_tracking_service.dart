import 'package:flutter/services.dart';

import '../../../core/config.dart';
import '../../camera/domain/camera_frame.dart';
import '../domain/hand_models.dart';
import '../domain/hand_tracking_service.dart';

class MediaPipeHandTrackingService implements HandTrackingService {
  MediaPipeHandTrackingService(this.config, {MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('autoscroll/hand_tracking');

  final TrackingConfig config;
  final MethodChannel _channel;

  @override
  Future<void> initialize() => _channel.invokeMethod<void>('initialize', {
    'detectionConfidence': config.detectionConfidence,
    'presenceConfidence': config.presenceConfidence,
    'trackingConfidence': config.trackingConfidence,
    'maximumInferenceDimension': config.maximumInferenceDimension,
  });

  @override
  Future<HandTrackingResult> track(CameraFrame frame) async {
    final data = await _channel.invokeMapMethod<String, Object?>('detect', {
      'width': frame.width,
      'height': frame.height,
      'rotation': frame.rotationDegrees,
      'timestampMs': frame.timestamp.inMilliseconds,
      'planes': frame.planes
          .map(
            (p) => {
              'bytes': p.bytes,
              'rowStride': p.rowStride,
              'pixelStride': p.pixelStride,
            },
          )
          .toList(growable: false),
    });
    if (data == null) throw const FormatException('Missing tracking response.');
    final points = data['landmarks'] as List<Object?>;
    final pose = points.isEmpty
        ? null
        : HandPose(
            landmarks: points.indexed
                .map((entry) {
                  final xyz = entry.$2 as List<Object?>;
                  if (xyz.length != 3) {
                    throw const FormatException('Expected xyz coordinates.');
                  }
                  return HandLandmark(
                    index: entry.$1,
                    x: (xyz[0] as num).toDouble(),
                    y: (xyz[1] as num).toDouble(),
                    z: (xyz[2] as num).toDouble(),
                  );
                })
                .toList(growable: false),
            handedness: data['handedness'] as String?,
            handednessConfidence: (data['handednessConfidence'] as num?)
                ?.toDouble(),
          );
    return HandTrackingResult(timestamp: frame.timestamp, pose: pose);
  }

  @override
  Future<void> dispose() => _channel.invokeMethod<void>('dispose');
}
