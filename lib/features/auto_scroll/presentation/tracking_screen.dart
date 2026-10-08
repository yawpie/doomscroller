import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../gesture/domain/hand_motion.dart';
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
    final pose = state.result?.pose;
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
                                        'Action: ${state.flick != null ? 'FLICK ${state.flick!.direction.name.toUpperCase()}' : switch (state.motion.action) {
                                                HandAction.moveUp => 'UP',
                                                HandAction.moveDown => 'DOWN',
                                                _ => 'IDLE',
                                              }}',
                                      ),
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
                          onChanged: (value) => setState(
                            () => scroll.configure(sensitivity: value),
                          ),
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
                              onChanged: (value) => setState(
                                () => scroll.configure(inverted: value),
                              ),
                            ),
                          ],
                        ),
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
                    subtitle: const Text(
                      'Move slowly to scroll; flick to move one page. Down goes ahead, up goes back.',
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
