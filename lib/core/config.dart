class TrackingConfig {
  const TrackingConfig({
    this.inferenceFps = 12,
    this.detectionConfidence = 0.5,
    this.presenceConfidence = 0.5,
    this.trackingConfidence = 0.5,
    this.maximumInferenceDimension = 320,
    this.lostAfter = const Duration(milliseconds: 750),
  }) : assert(inferenceFps > 0 && inferenceFps <= 30),
       assert(detectionConfidence >= 0 && detectionConfidence <= 1),
       assert(presenceConfidence >= 0 && presenceConfidence <= 1),
       assert(trackingConfidence >= 0 && trackingConfidence <= 1),
       assert(
         maximumInferenceDimension >= 192 && maximumInferenceDimension <= 1920,
       );

  final int inferenceFps;
  final double detectionConfidence;
  final double presenceConfidence;
  final double trackingConfidence;
  final int maximumInferenceDimension;
  final Duration lostAfter;

  Duration get frameInterval =>
      Duration(microseconds: (1000000 / inferenceFps).ceil());
}

class MotionDetectionConfig {
  const MotionDetectionConfig({
    this.smoothingAlpha = 0.4,
    this.windowSize = 6,
    this.minimumFrames = 4,
    this.movementThreshold = 0.018,
    this.deadZone = 0.008,
    this.minimumVelocity = 0.025,
    this.confirmationFrames = 2,
    this.directionCooldown = const Duration(milliseconds: 150),
    this.maximumSampleGap = const Duration(milliseconds: 350),
    this.maximumPositionJump = 0.15,
    this.stationaryVelocity = 0.015,
    this.calibrationDuration = const Duration(milliseconds: 600),
    this.calibrationRadius = 0.012,
  }) : assert(smoothingAlpha > 0 && smoothingAlpha <= 1),
       assert(windowSize >= minimumFrames && minimumFrames >= 3),
       assert(deadZone >= 0 && movementThreshold >= deadZone),
       assert(minimumVelocity >= 0),
       assert(confirmationFrames >= 1),
       assert(maximumPositionJump > 0 && maximumPositionJump <= 1),
       assert(stationaryVelocity >= 0),
       assert(calibrationRadius > 0);

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
  final double maximumPositionJump;
  final double stationaryVelocity;
  final Duration calibrationDuration;
  final double calibrationRadius;
}

class ScrollConfig {
  const ScrollConfig({
    this.pixelsPerNormalizedUnit = 1800,
    this.maximumPixelsPerSecond = 1200,
    this.staleAfter = const Duration(milliseconds: 350),
    this.deadZone = 0.025,
    this.smoothingAlpha = 0.3,
    this.minimumSensitivity = 300,
    this.maximumSensitivity = 4000,
  }) : assert(pixelsPerNormalizedUnit > 0),
       assert(maximumPixelsPerSecond > 0),
       assert(deadZone >= 0),
       assert(smoothingAlpha > 0 && smoothingAlpha <= 1),
       assert(
         minimumSensitivity > 0 &&
             maximumSensitivity >= pixelsPerNormalizedUnit &&
             minimumSensitivity <= pixelsPerNormalizedUnit,
       );

  final double pixelsPerNormalizedUnit;
  final double maximumPixelsPerSecond;
  final Duration staleAfter;
  final double deadZone;
  final double smoothingAlpha;
  final double minimumSensitivity;
  final double maximumSensitivity;
}
