import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';

class TrackingScreen extends ConsumerStatefulWidget {
  const TrackingScreen({super.key});

  @override
  ConsumerState<TrackingScreen> createState() => _TrackingScreenState();
}

class _TrackingScreenState extends ConsumerState<TrackingScreen>
    with WidgetsBindingObserver {
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
    return Scaffold(
      appBar: AppBar(title: const Text('Hand Scroll Lab')),
      body: SafeArea(
        child: Column(
          children: [
            SizedBox(
              height: MediaQuery.sizeOf(context).height * 0.25,
              child:
                  state.running && camera != null && camera.value.isInitialized
                  ? Center(
                      child: AspectRatio(
                        aspectRatio: 1 / camera.value.aspectRatio,
                        child: CameraPreview(camera),
                      ),
                    )
                  : const Center(child: Text('Front camera preview')),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    state.result?.handDetected == true
                        ? 'Hand detected'
                        : 'No hand detected',
                  ),
                  Text('Action: ${state.motion.action.name}'),
                  Text(
                    'Vertical velocity: ${state.motion.verticalVelocity.toStringAsFixed(3)} heights/s',
                  ),
                  Text(
                    'Inference FPS: ${state.inferenceFps.toStringAsFixed(1)}',
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
                    icon: Icon(state.running ? Icons.stop : Icons.play_arrow),
                    label: Text(
                      state.busy
                          ? 'Preparing…'
                          : state.running
                          ? 'Stop tracking'
                          : 'Start tracking',
                    ),
                  ),
                ],
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
                    'Move your hand down to read ahead, or up to go back.',
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
