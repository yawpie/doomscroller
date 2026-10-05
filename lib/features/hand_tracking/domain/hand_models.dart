class HandLandmark {
  const HandLandmark({required this.x, required this.y, required this.z});

  final double x;
  final double y;
  // Relative depth; unlike x/y this is not restricted to [0, 1].
  final double z;
}

class HandPose {
  HandPose({
    required List<HandLandmark> landmarks,
    this.handedness,
    this.handednessConfidence,
  }) : landmarks = List.unmodifiable(landmarks) {
    if (landmarks.length != 21 ||
        landmarks.any(
          (p) =>
              !p.x.isFinite ||
              !p.y.isFinite ||
              !p.z.isFinite ||
              p.x < 0 ||
              p.x > 1 ||
              p.y < 0 ||
              p.y > 1,
        )) {
      throw ArgumentError('A pose requires 21 finite normalized landmarks.');
    }
    final confidence = handednessConfidence;
    if (confidence != null &&
        (!confidence.isFinite || confidence < 0 || confidence > 1)) {
      throw ArgumentError.value(confidence, 'handednessConfidence');
    }
  }

  final List<HandLandmark> landmarks;
  final String? handedness;
  // MediaPipe exposes handedness score, not per-result detection confidence.
  final double? handednessConfidence;

  double get palmY =>
      [0, 5, 9, 13, 17].fold<double>(0, (sum, i) => sum + landmarks[i].y) / 5;
}

class HandTrackingResult {
  const HandTrackingResult({required this.timestamp, this.pose});

  final Duration timestamp;
  final HandPose? pose;
  bool get handDetected => pose != null;
}
