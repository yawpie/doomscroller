import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/config.dart';
import '../../gesture/application/gesture_coordinator.dart';
import '../../gesture/domain/hand_motion.dart';
import '../../gesture/domain/pose_event.dart';
import 'hand_overlay.dart';

class TrackingScreen extends ConsumerStatefulWidget {
  const TrackingScreen({super.key});

  @override
  ConsumerState<TrackingScreen> createState() => _TrackingScreenState();
}

class _TrackingScreenState extends ConsumerState<TrackingScreen>
    with WidgetsBindingObserver {
  bool _debug = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    unawaited(
      ref
          .read(trackingProvider.notifier)
          .controller
          .setForeground(state == AppLifecycleState.resumed),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(trackingProvider);
    final coordinator = ref.read(trackingProvider.notifier).controller;
    final camera = ref.watch(cameraServiceProvider).previewController;
    final scroll = ref.watch(flutterScrollServiceProvider);
    final gestureMode = ref.watch(gestureModeProvider);
    final poseConfig = ref.watch(handPoseConfigProvider);
    final pose = state.result?.pose;
    final action =
        state.lastPose != null &&
            (state.posePhase == HandPosePhase.latched ||
                state.posePhase == HandPosePhase.waitingRearm)
        ? 'PAGE ${state.lastPose!.pose == RecognizedHandPose.pointingDown ? 'DOWN' : 'UP'}'
        : state.flick != null
        ? 'FLICK ${state.flick!.direction.name.toUpperCase()}'
        : switch (state.motion.action) {
            HandAction.moveUp => 'UP',
            HandAction.moveDown => 'DOWN',
            _ => 'IDLE',
          };
    return Scaffold(
      appBar: AppBar(title: const Text('Hand Scroll Lab')),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) => Column(
            children: [
              SizedBox(
                height: constraints.maxHeight * 0.65,
                child: SingleChildScrollView(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          height: (constraints.maxHeight * 0.32).clamp(
                            180.0,
                            240.0,
                          ),
                          child: Row(
                            children: [
                              Expanded(
                                flex: 2,
                                child:
                                    state.running &&
                                        camera != null &&
                                        camera.value.isInitialized
                                    ? Center(
                                        child: AspectRatio(
                                          aspectRatio:
                                              1 / camera.value.aspectRatio,
                                          child: CameraPreview(
                                            camera,
                                            child: _debug && pose != null
                                                ? IgnorePointer(
                                                    child: CustomPaint(
                                                      painter: HandOverlay(
                                                        pose,
                                                        state.motion,
                                                      ),
                                                    ),
                                                  )
                                                : null,
                                          ),
                                        ),
                                      )
                                    : const Center(
                                        child: Text(
                                          'Front camera preview',
                                          textAlign: TextAlign.center,
                                        ),
                                      ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                flex: 3,
                                child: SingleChildScrollView(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      Text(
                                        pose != null
                                            ? 'Hand detected'
                                            : 'No hand detected',
                                      ),
                                      Text(
                                        'Tracking: ${state.phase.name.toUpperCase()}',
                                      ),
                                      Text(
                                        'Hand pose: ${switch (state.currentPose) {
                                          RecognizedHandPose.pointingUp => 'Pointing Up',
                                          RecognizedHandPose.pointingDown => 'Pointing Down',
                                          RecognizedHandPose.neutral => 'Open hand',
                                          RecognizedHandPose.unknown => 'Unknown',
                                        }}',
                                      ),
                                      Text(
                                        'Pose state: ${state.posePhase == HandPosePhase.waitingRearm ? 'WAITING_REARM' : state.posePhase.name.toUpperCase()}',
                                      ),
                                      if (state.posePhase ==
                                          HandPosePhase.confirming)
                                        Text(
                                          'Confirming: ${(state.confirmationProgress * 100).round()}%',
                                        ),
                                      if (state.posePhase ==
                                              HandPosePhase.latched ||
                                          state.posePhase ==
                                              HandPosePhase.waitingRearm)
                                        Text(
                                          poseConfig.rearmMode ==
                                                  HandPoseRearmMode.stablePose
                                              ? 'Re-arm: Hold same pose for ${poseConfig.stablePoseDuration.inMilliseconds} ms · cooldown ${state.cooldownRemaining.inMilliseconds} ms'
                                              : state.posePhase ==
                                                    HandPosePhase.latched
                                              ? 'Re-arm: Show an open hand'
                                              : 'Re-arm: Hold open hand · cooldown ${state.cooldownRemaining.inMilliseconds} ms',
                                        ),
                                      if (state.lastPose != null)
                                        Text(
                                          'Last pose action: Page ${state.lastPose!.pose == RecognizedHandPose.pointingDown ? 'Down' : 'Up'} at ${state.lastPose!.timestamp.inMilliseconds} ms',
                                        ),
                                      Text('Action: $action'),
                                      Text(
                                        'Hand velocity: ${state.motion.verticalVelocity.toStringAsFixed(3)} heights/s',
                                      ),
                                      ValueListenableBuilder<double>(
                                        valueListenable: scroll.velocity,
                                        builder: (context, value, child) => Text(
                                          'Scroll velocity: ${value.toStringAsFixed(0)} px/s',
                                        ),
                                      ),
                                      Text(
                                        'Inference FPS: ${state.inferenceFps.toStringAsFixed(1)}',
                                      ),
                                      Text(
                                        state.motion.calibrated
                                            ? 'Neutral Y: ${state.motion.neutralY!.toStringAsFixed(3)}'
                                            : 'Hold hand still to calibrate',
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        if (_debug && pose != null)
                          Text(
                            'Palm: X ${pose.palmX.toStringAsFixed(3)}, Y ${pose.palmY.toStringAsFixed(3)} · ${state.inferenceMilliseconds.toStringAsFixed(0)} ms/frame',
                          ),
                        if (state.error != null)
                          Text(
                            state.error!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        FilledButton.icon(
                          onPressed: state.busy
                              ? null
                              : () => unawaited(
                                  state.running
                                      ? coordinator.stop()
                                      : coordinator.start(),
                                ),
                          icon: Icon(
                            state.running ? Icons.stop : Icons.play_arrow,
                          ),
                          label: Text(
                            state.busy
                                ? 'Preparing…'
                                : state.running
                                ? 'Stop tracking'
                                : 'Start tracking',
                          ),
                        ),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Expanded(child: Text('Sensitivity')),
                            Text(scroll.mapper.sensitivity.toStringAsFixed(0)),
                          ],
                        ),
                        Slider(
                          value: scroll.mapper.sensitivity,
                          min: scroll.config.minimumSensitivity,
                          max: scroll.config.maximumSensitivity,
                          label: scroll.mapper.sensitivity.toStringAsFixed(0),
                          onChanged: (value) => setState(() {
                            scroll.configure(sensitivity: value);
                            coordinator.updateGestureSettings();
                          }),
                        ),
                        Wrap(
                          crossAxisAlignment: WrapCrossAlignment.center,
                          spacing: 8,
                          children: [
                            const Text('Debug overlay'),
                            Switch(
                              value: _debug,
                              onChanged: (value) =>
                                  setState(() => _debug = value),
                            ),
                            const Text('Invert scroll'),
                            Switch(
                              value: scroll.mapper.inverted,
                              onChanged: (value) => setState(() {
                                scroll.configure(inverted: value);
                                coordinator.updateGestureSettings();
                              }),
                            ),
                            const Text('Pose only'),
                            Switch(
                              value: gestureMode == GestureMode.poseOnly,
                              onChanged: (value) => ref
                                  .read(gestureModeProvider.notifier)
                                  .setMode(
                                    value
                                        ? GestureMode.poseOnly
                                        : GestureMode.combined,
                                  ),
                            ),
                          ],
                        ),
                        DropdownButtonFormField<HandPoseRearmMode>(
                          isExpanded: true,
                          key: ValueKey(poseConfig.rearmMode),
                          initialValue: poseConfig.rearmMode,
                          decoration: const InputDecoration(
                            labelText: 'Pose re-arm',
                          ),
                          items: const [
                            DropdownMenuItem(
                              value: HandPoseRearmMode.openHand,
                              child: Text('Open hand'),
                            ),
                            DropdownMenuItem(
                              value: HandPoseRearmMode.stablePose,
                              child: Text('Hold same pose'),
                            ),
                          ],
                          onChanged: (value) {
                            if (value != null) {
                              ref
                                  .read(handPoseConfigProvider.notifier)
                                  .configureRearm(mode: value);
                            }
                          },
                        ),
                        if (poseConfig.rearmMode ==
                            HandPoseRearmMode.stablePose) ...[
                          Text(
                            'Re-arm after: ${poseConfig.stablePoseDuration.inMilliseconds} ms',
                          ),
                          Slider(
                            value: poseConfig.stablePoseDuration.inMilliseconds
                                .toDouble()
                                .clamp(300, 3000),
                            min: 300,
                            max: 3000,
                            divisions: 27,
                            label:
                                '${poseConfig.stablePoseDuration.inMilliseconds} ms',
                            onChanged: (value) => ref
                                .read(handPoseConfigProvider.notifier)
                                .configureRearm(
                                  duration: Duration(
                                    milliseconds: value.round(),
                                  ),
                                ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: ListView.builder(
                  controller: scroll.controller,
                  itemCount: 80,
                  itemBuilder: (context, index) => ListTile(
                    title: Text('Reading item ${index + 1}'),
                    subtitle: Text(
                      poseConfig.rearmMode == HandPoseRearmMode.openHand
                          ? 'Point up/down to move one page. Open your hand before pointing again. Slow movement and flicks work in combined mode.'
                          : 'Point up/down to move one page. Hold the same pose for ${poseConfig.stablePoseDuration.inMilliseconds} ms to re-arm and repeat. Slow movement and flicks work in combined mode.',
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
