import 'dart:math' as math;

import '../../../core/config.dart';
import '../../hand_tracking/domain/hand_models.dart';
import '../domain/pose_event.dart';
import 'hand_pose_classifier.dart';

class HandPoseDetector {
  HandPoseDetector(this.config) : _classifier = HandPoseClassifier(config) {
    if (config.confirmationDuration <= Duration.zero ||
        config.releaseDuration <= Duration.zero ||
        config.stablePoseDuration <= Duration.zero ||
        config.cooldown < Duration.zero ||
        config.minimumObservations < 2 ||
        config.maximumSampleGap <= Duration.zero) {
      throw ArgumentError('Invalid hand pose durations or observation count.');
    }
  }

  final HandPoseDetectionConfig config;
  final HandPoseClassifier _classifier;
  HandPosePhase _phase = HandPosePhase.ready;
  RecognizedHandPose _currentPose = RecognizedHandPose.unknown;
  RecognizedHandPose? _activePose;
  Duration? _lastTime;
  Duration? _candidateStart;
  int _observations = 0;
  Duration? _rearmStart;
  int _rearmObservations = 0;
  RecognizedHandPose? _rearmPose;
  PoseEvent? _lastEvent;

  HandPosePhase get phase => _phase;
  RecognizedHandPose get currentPose => _currentPose;
  PoseEvent? get lastEvent => _lastEvent;
  bool get suppressesContinuous => _phase != HandPosePhase.ready;

  double get confirmationProgress {
    final start = _candidateStart;
    final time = _lastTime;
    if (_phase != HandPosePhase.confirming || start == null || time == null) {
      return 0;
    }
    return math.min(
      ((time - start).inMicroseconds /
              config.confirmationDuration.inMicroseconds)
          .clamp(0.0, 1.0),
      _observations / config.minimumObservations,
    );
  }

  Duration get cooldownRemaining {
    final event = _lastEvent;
    final time = _lastTime;
    if (event == null || time == null) return Duration.zero;
    final remaining = config.cooldown - (time - event.timestamp);
    return remaining > Duration.zero ? remaining : Duration.zero;
  }

  PoseEvent? process(HandTrackingResult result) {
    if (!config.enabled) return null;
    final time = result.timestamp;
    final previous = _lastTime;
    if (time < Duration.zero || (previous != null && time <= previous)) {
      loseTracking();
      return null;
    }
    _lastTime = time;
    if (result.pose == null) {
      loseTracking();
      return null;
    }
    if (previous != null && time - previous > config.maximumSampleGap) {
      _discardEvidence();
    }
    _currentPose = _classifier.classify(
      result.pose!.landmarks,
      activePose: _activePose,
    );
    switch (_phase) {
      case HandPosePhase.ready:
        if (_isPointing(_currentPose)) _beginCandidate(time);
      case HandPosePhase.confirming:
        if (_currentPose != _activePose) {
          _discardEvidence();
          if (_isPointing(_currentPose)) _beginCandidate(time);
          return null;
        }
        if (_observations < config.minimumObservations) _observations++;
        if (_observations >= config.minimumObservations &&
            time - _candidateStart! >= config.confirmationDuration) {
          final event = PoseEvent(pose: _activePose!, timestamp: time);
          _lastEvent = event;
          _phase = HandPosePhase.latched;
          _candidateStart = null;
          _observations = 0;
          if (config.rearmMode == HandPoseRearmMode.stablePose) {
            _rearmStart = time;
            _rearmObservations = 1;
            _rearmPose = _currentPose;
          }
          return event;
        }
      case HandPosePhase.latched:
      case HandPosePhase.waitingRearm:
        _updateRearm(time);
    }
    return null;
  }

  void _updateRearm(Duration time) {
    final openHand = config.rearmMode == HandPoseRearmMode.openHand;
    final valid = openHand
        ? _currentPose == RecognizedHandPose.neutral
        : _currentPose != RecognizedHandPose.unknown;
    if (!valid) {
      _discardEvidence();
      return;
    }
    _phase = HandPosePhase.waitingRearm;
    if (_rearmStart == null || _rearmPose != _currentPose) {
      _rearmStart = time;
      _rearmObservations = 1;
      _rearmPose = _currentPose;
      return;
    }
    if (_rearmObservations < 2) _rearmObservations++;
    final duration = openHand
        ? config.releaseDuration
        : config.stablePoseDuration;
    if (_rearmObservations >= 2 &&
        time - _rearmStart! >= duration &&
        time - _lastEvent!.timestamp >= config.cooldown) {
      _phase = HandPosePhase.ready;
      _activePose = null;
      _discardEvidence();
    }
  }

  void _beginCandidate(Duration time) {
    _phase = HandPosePhase.confirming;
    _activePose = _currentPose;
    _candidateStart = time;
    _observations = 1;
  }

  bool _isPointing(RecognizedHandPose pose) =>
      pose == RecognizedHandPose.pointingUp ||
      pose == RecognizedHandPose.pointingDown;

  void _discardEvidence() {
    _candidateStart = null;
    _observations = 0;
    _rearmStart = null;
    _rearmObservations = 0;
    _rearmPose = null;
    if (_phase == HandPosePhase.confirming) {
      _phase = HandPosePhase.ready;
      _activePose = null;
    } else if (_phase == HandPosePhase.waitingRearm) {
      _phase = HandPosePhase.latched;
    }
  }

  void loseTracking() {
    _currentPose = RecognizedHandPose.unknown;
    _discardEvidence();
  }

  void reset() {
    _phase = HandPosePhase.ready;
    _currentPose = RecognizedHandPose.unknown;
    _activePose = null;
    _lastTime = null;
    _candidateStart = null;
    _observations = 0;
    _rearmStart = null;
    _rearmObservations = 0;
    _rearmPose = null;
    _lastEvent = null;
  }
}
