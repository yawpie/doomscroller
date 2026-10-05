class HandLandmark {
  const HandLandmark({
    required this.index,
    required this.x,
    required this.y,
    required this.z,
  });

  final int index;
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
        landmarks.indexed.any(
          (entry) =>
              entry.$2.index != entry.$1 ||
              !entry.$2.x.isFinite ||
              !entry.$2.y.isFinite ||
              !entry.$2.z.isFinite ||
              entry.$2.x < 0 ||
              entry.$2.x > 1 ||
              entry.$2.y < 0 ||
              entry.$2.y > 1,
        )) {
      throw ArgumentError(
        'A pose requires 21 ordered indexed finite normalized landmarks.',
      );
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
  double get palmX =>
      [0, 5, 9, 13, 17].fold<double>(0, (sum, i) => sum + landmarks[i].x) / 5;
  ({double normalizedX, double normalizedY}) get palmCenter =>
      (normalizedX: palmX, normalizedY: palmY);
}

class HandTrackingResult {
  const HandTrackingResult({required this.timestamp, this.pose});

  final Duration timestamp;
  final HandPose? pose;
  bool get handDetected => pose != null;
}
