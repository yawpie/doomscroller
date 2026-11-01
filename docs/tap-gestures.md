# Pose taps and navigation

POSE and HYBRID use one canonical recognition pipeline for preset and custom
bindings. With custom actions off, pointing up/down navigates, an open hand sends
one center tap, and a thumbs-up sends a double tap. Pointing requires the thumb
and other fingers folded; an open hand requires all five fingers extended.
Direction reversal applies to preset navigation but not taps.

A discrete action fires once per appearance. Show another valid pose long enough
to release it; loss or a target change alone cannot rearm it. The pose cooldown
also limits successive actions (200–3000ms, default 450ms). Custom actions may
instead assign a held scroll or NONE. Practice uses the same recognition locally.
Actual target behavior and camera accuracy require device validation.
