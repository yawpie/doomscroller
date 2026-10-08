class TrackingConfig {
  const TrackingConfig({
    this.inferenceFps = 15,
    this.detectionConfidence = 0.4,
    this.presenceConfidence = 0.4,
    this.trackingConfidence = 0.6,
    this.maximumInferenceDimension = 320,
    this.lostAfter = const Duration(milliseconds: 500),
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
    this.windowSize = 4,
    this.minimumFrames = 3,
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

// Thresholds are experimental and should be tuned on the target phone.
class FlickDetectionConfig {
  const FlickDetectionConfig({
    this.minimumVelocity = 0.4,
    this.minimumDisplacement = 0.10,
    this.minimumDuration = const Duration(milliseconds: 80),
    this.maximumDuration = const Duration(milliseconds: 350),
    this.cooldown = const Duration(milliseconds: 500),
    this.directionConsistency = 0.75,
    this.maximumSampleGap = const Duration(milliseconds: 250),
    this.maximumPositionJump = 0.25,
    this.rearmVelocity = 0.15,
    this.rearmDuration = const Duration(milliseconds: 150),
    this.rearmRadius = 0.012,
  }) : assert(minimumVelocity > 0),
       assert(minimumDisplacement > 0 && minimumDisplacement <= 1),
       assert(directionConsistency > 0 && directionConsistency <= 1),
       assert(maximumPositionJump > 0 && maximumPositionJump <= 1),
       assert(rearmVelocity >= 0 && rearmVelocity < minimumVelocity * 0.5),
       assert(rearmRadius > 0 && rearmRadius < minimumDisplacement);

  final double minimumVelocity;
  final double minimumDisplacement;
  final Duration minimumDuration;
  final Duration maximumDuration;
  final Duration cooldown;
  final double directionConsistency;
  final Duration maximumSampleGap;
  final double maximumPositionJump;
  final double rearmVelocity;
  final Duration rearmDuration;
  final double rearmRadius;
}

enum FlickScrollAction { page, fastScroll }

class ScrollConfig {
  const ScrollConfig({
    this.pixelsPerNormalizedUnit = 1800,
    this.maximumPixelsPerSecond = 1200,
    this.staleAfter = const Duration(milliseconds: 350),
    this.deadZone = 0.025,
    this.smoothingAlpha = 0.3,
    this.minimumSensitivity = 300,
    this.maximumSensitivity = 4000,
    this.flickAction = FlickScrollAction.page,
    this.flickScrollDistance = 600,
    this.flickDuration = const Duration(milliseconds: 220),
  }) : assert(pixelsPerNormalizedUnit > 0),
       assert(maximumPixelsPerSecond > 0),
       assert(deadZone >= 0),
       assert(smoothingAlpha > 0 && smoothingAlpha <= 1),
       assert(flickScrollDistance > 0),
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
  final FlickScrollAction flickAction;
  final double flickScrollDistance;
  final Duration flickDuration;
}

// Angles and palm-relative lengths are experimental target-device thresholds.
enum HandPoseRearmMode { openHand, stablePose }

class HandPoseDetectionConfig {
  const HandPoseDetectionConfig({
    this.enabled = true,
    this.confirmationDuration = const Duration(milliseconds: 180),
    this.releaseDuration = const Duration(milliseconds: 180),
    this.cooldown = const Duration(milliseconds: 450),
    this.rearmMode = HandPoseRearmMode.openHand,
    this.stablePoseDuration = const Duration(milliseconds: 1000),
    this.minimumObservations = 3,
    this.minimumIndexAngle = 160,
    this.maximumCurledAngle = 110,
    this.minimumVerticalAlignment = 0.85,
    this.exitIndexAngle = 150,
    this.exitCurledAngle = 125,
    this.exitVerticalAlignment = 0.70,
    this.minimumFingerLengthRatio = 0.35,
    this.minimumBoneLengthRatio = 0.03,
    this.maximumSampleGap = const Duration(milliseconds: 250),
  });

  final bool enabled;
  final Duration confirmationDuration;
  final Duration releaseDuration;
  final Duration cooldown;
  final HandPoseRearmMode rearmMode;
  final Duration stablePoseDuration;
  final int minimumObservations;
  final double minimumIndexAngle;
  final double maximumCurledAngle;
  final double minimumVerticalAlignment;
  final double exitIndexAngle;
  final double exitCurledAngle;
  final double exitVerticalAlignment;
  final double minimumFingerLengthRatio;
  final double minimumBoneLengthRatio;
  final Duration maximumSampleGap;

  HandPoseDetectionConfig copyWith({
    HandPoseRearmMode? rearmMode,
    Duration? stablePoseDuration,
  }) => HandPoseDetectionConfig(
    enabled: enabled,
    confirmationDuration: confirmationDuration,
    releaseDuration: releaseDuration,
    cooldown: cooldown,
    rearmMode: rearmMode ?? this.rearmMode,
    stablePoseDuration: stablePoseDuration ?? this.stablePoseDuration,
    minimumObservations: minimumObservations,
    minimumIndexAngle: minimumIndexAngle,
    maximumCurledAngle: maximumCurledAngle,
    minimumVerticalAlignment: minimumVerticalAlignment,
    exitIndexAngle: exitIndexAngle,
    exitCurledAngle: exitCurledAngle,
    exitVerticalAlignment: exitVerticalAlignment,
    minimumFingerLengthRatio: minimumFingerLengthRatio,
    minimumBoneLengthRatio: minimumBoneLengthRatio,
    maximumSampleGap: maximumSampleGap,
  );
}
