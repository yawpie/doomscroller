import '../../../core/config.dart';
import '../../hand_tracking/domain/hand_models.dart';
import '../domain/flick_event.dart';

class FlickDetector {
  FlickDetector(this.config) {
    final thresholds = [
      config.minimumVelocity,
      config.minimumDisplacement,
      config.directionConsistency,
      config.maximumPositionJump,
      config.rearmVelocity,
      config.rearmRadius,
    ];
    if (thresholds.any((value) => !value.isFinite) ||
        config.minimumVelocity <= 0 ||
        config.minimumDisplacement <= 0 ||
        config.minimumDisplacement > 1 ||
        config.directionConsistency <= 0 ||
        config.directionConsistency > 1 ||
        config.maximumPositionJump <= 0 ||
        config.maximumPositionJump > 1 ||
        config.rearmVelocity < 0 ||
        config.rearmVelocity >= config.minimumVelocity * 0.5 ||
        config.rearmRadius <= 0 ||
        config.rearmRadius >= config.minimumDisplacement ||
        config.minimumDuration <= Duration.zero ||
        config.maximumDuration < config.minimumDuration ||
        config.cooldown < Duration.zero ||
        config.maximumSampleGap <= Duration.zero ||
        config.rearmDuration <= Duration.zero) {
      throw ArgumentError('Invalid flick detection thresholds or durations.');
    }
  }

  final FlickDetectionConfig config;
  Duration? _previousTime;
  double? _previousX;
  double? _previousY;
  Duration? _candidateStart;
  double _candidateY = 0;
  double _path = 0;
  int _sampleCount = 0;
  FlickDirection? _direction;
  Duration? _lastFlickAt;
  bool _held = false;
  bool _suppressed = false;
  Duration? _stableSince;
  double? _stableX;
  double? _stableY;

  bool get suppressesContinuous => _suppressed;

  FlickEvent? process(HandTrackingResult result) {
    _suppressed = _held;
    final time = result.timestamp;
    final previousTime = _previousTime;
    if (time < Duration.zero ||
        (previousTime != null && time <= previousTime)) {
      _hold();
      return null;
    }
    final pose = result.pose;
    if (pose == null) {
      loseTracking();
      return null;
    }
    final x = pose.palmX;
    final y = pose.palmY;
    final previousX = _previousX;
    final previousY = _previousY;
    _previousTime = time;
    _previousX = x;
    _previousY = y;
    if (previousTime == null || previousX == null || previousY == null) {
      if (_held) _rearm(time, x, y, 0);
      _suppressed = _held;
      return null;
    }
    final gap = time - previousTime;
    final dx = x - previousX;
    final dy = y - previousY;
    final jump = config.maximumPositionJump;
    if (gap > config.maximumSampleGap || dx * dx + dy * dy > jump * jump) {
      _hold();
      return null;
    }
    final velocity = dy / (gap.inMicroseconds / 1000000);
    if (_held) {
      _rearm(time, x, y, velocity);
      _suppressed = _held;
      return null;
    }
    if (_candidateStart == null) {
      if (velocity.abs() >= config.minimumVelocity) {
        _candidateStart = previousTime;
        _candidateY = previousY;
        _direction = dy < 0 ? FlickDirection.up : FlickDirection.down;
        _path = dy.abs();
        _sampleCount = 2;
        _suppressed = true;
      }
      return null;
    }

    _suppressed = true;
    _path += dy.abs();
    _sampleCount++;
    final duration = time - _candidateStart!;
    final reversed = _direction == FlickDirection.up ? dy > 0 : dy < 0;
    if ((reversed && dy.abs() > config.rearmRadius) ||
        velocity.abs() < config.minimumVelocity * 0.5 ||
        duration > config.maximumDuration) {
      _hold();
      return null;
    }
    final displacement = y - _candidateY;
    final meanVelocity = displacement / (duration.inMicroseconds / 1000000);
    final sameDirection = _direction == FlickDirection.up
        ? displacement < 0
        : displacement > 0;
    // Two movement intervals reject a single plausible-sized tracking jump.
    if (_sampleCount < 3 ||
        duration < config.minimumDuration ||
        displacement.abs() < config.minimumDisplacement ||
        meanVelocity.abs() < config.minimumVelocity ||
        !sameDirection ||
        _path == 0 ||
        displacement.abs() / _path < config.directionConsistency) {
      return null;
    }
    final event = FlickEvent(
      direction: _direction!,
      timestamp: time,
      verticalDisplacement: displacement,
      verticalVelocity: meanVelocity,
      duration: duration,
    );
    _lastFlickAt = time;
    _hold();
    return event;
  }

  void _hold() {
    _held = true;
    _suppressed = true;
    _clearCandidate();
    _clearStable();
  }

  void _rearm(Duration time, double x, double y, double velocity) {
    final stableX = _stableX;
    final stableY = _stableY;
    final radius = config.rearmRadius;
    final moved =
        stableX != null &&
        stableY != null &&
        (x - stableX) * (x - stableX) + (y - stableY) * (y - stableY) >
            radius * radius;
    if (velocity.abs() > config.rearmVelocity || moved) {
      _clearStable();
      return;
    }
    _stableSince ??= time;
    _stableX ??= x;
    _stableY ??= y;
    final lastFlick = _lastFlickAt;
    final cooledDown = lastFlick == null || time - lastFlick >= config.cooldown;
    if (cooledDown && time - _stableSince! >= config.rearmDuration) {
      _held = false;
      _clearStable();
    }
  }

  void _clearCandidate() {
    _candidateStart = null;
    _candidateY = _path = 0;
    _sampleCount = 0;
    _direction = null;
  }

  void _clearStable() {
    _stableSince = null;
    _stableX = _stableY = null;
  }

  void loseTracking() {
    if (_candidateStart != null) _held = true;
    _previousTime = null;
    _previousX = _previousY = null;
    _clearCandidate();
    _clearStable();
    // A missing hand must not bypass the cooldown of a completed flick.
    _suppressed = true;
  }

  void reset() {
    _previousTime = _lastFlickAt = null;
    _previousX = _previousY = null;
    _held = _suppressed = false;
    _clearCandidate();
    _clearStable();
  }
}
