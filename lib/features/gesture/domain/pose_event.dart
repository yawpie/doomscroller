enum RecognizedHandPose { pointingUp, pointingDown, neutral, unknown }

enum HandPosePhase { ready, confirming, latched, waitingRearm }

class PoseEvent {
  const PoseEvent({required this.pose, required this.timestamp});

  final RecognizedHandPose pose;
  final Duration timestamp;
}
