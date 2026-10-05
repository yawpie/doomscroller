class TrackingConfig {
  const TrackingConfig({
    this.inferenceFps = 12,
    this.detectionConfidence = 0.5,
    this.presenceConfidence = 0.5,
    this.trackingConfidence = 0.5,
  }) : assert(inferenceFps > 0 && inferenceFps <= 30),
       assert(detectionConfidence >= 0 && detectionConfidence <= 1),
       assert(presenceConfidence >= 0 && presenceConfidence <= 1),
       assert(trackingConfidence >= 0 && trackingConfidence <= 1);

  final int inferenceFps;
  final double detectionConfidence;
  final double presenceConfidence;
  final double trackingConfidence;

  Duration get frameInterval =>
      Duration(microseconds: (1000000 / inferenceFps).ceil());
}

class MotionConfig {
  const MotionConfig({
    this.smoothingAlpha = 0.4,
    this.windowSize = 6,
    this.minimumFrames = 4,
    this.movementThreshold = 0.018,
    this.deadZone = 0.008,
    this.minimumVelocity = 0.025,
    this.confirmationFrames = 2,
    this.directionCooldown = const Duration(milliseconds: 150),
    this.maximumSampleGap = const Duration(milliseconds: 350),
  }) : assert(smoothingAlpha > 0 && smoothingAlpha <= 1),
       assert(windowSize >= minimumFrames && minimumFrames >= 3),
       assert(deadZone >= 0 && movementThreshold >= deadZone),
       assert(minimumVelocity >= 0),
       assert(confirmationFrames >= 1);

  final double smoothingAlpha;
  final int windowSize;
  final int minimumFrames;
  // Displacements are fractions of the upright image height.
  final double movementThreshold;
  final double deadZone;
  final double minimumVelocity;
  final int confirmationFrames;
  final Duration directionCooldown;
  final Duration maximumSampleGap;
}

class ScrollConfig {
  const ScrollConfig({
    this.pixelsPerNormalizedUnit = 1800,
    this.maximumPixelsPerSecond = 1200,
    this.staleAfter = const Duration(milliseconds: 350),
  }) : assert(pixelsPerNormalizedUnit > 0),
       assert(maximumPixelsPerSecond > 0);

  final double pixelsPerNormalizedUnit;
  final double maximumPixelsPerSecond;
  final Duration staleAfter;
}
