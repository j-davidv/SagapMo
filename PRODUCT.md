# SagapMo?

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

The native pilot uses Kotlin, Jetpack Compose, Room, and MapLibre Compose on Android 12+. The design reference is a standalone HTML/CSS/JavaScript artifact, as selected by the user; it is not the Android implementation.

## Users

Students at Batangas State University, Alangilan Campus, recording individual outdoor Wi-Fi and mobile signal samples while moving around campus.

## Product Purpose

Record a real signal measurement on demand, keep it across restarts, and inspect it in a list or as a map pin when a usable location exists.

## Capabilities and Constraints

The behavioral baseline is `docs/superpowers/specs/2026-11-17-sagapmo-pilot-design.md`, with later user revisions summarized in `README.md`. One user-initiated operation produces at most one saved reading. The active default network chooses Wi-Fi or Mobile; filters never choose the measured connection. A valid signal survives missing location. All saved readings remain available without a basemap. Current revisions add approximate and manual locations, optional location descriptions saved automatically, and confirmed deletion with temporary Undo. No accounts, server, shared data, export, continuous monitoring, latency tests, heatmaps, or downloaded map packs are included.

## Brand Commitments

Name: SagapMo?. The user supplied `design-ref/` as the starting design and requested usability improvements. Preserve its collegiate crimson identity, map/readings destinations, and bottom-anchored recording action. The reference's extra telemetry fields are not product requirements.

## Evidence on Hand

Approved behavioral spec and two supplied screen references. No real pilot readings, verified landmark geometry, or final logo were supplied. All prototype measurements and geometry must be explicitly labeled illustrative.

## Product Principles

- Make recording and finding a reading the shortest paths.
- Confirm saving only after storage succeeds.
- Explain missing pins without treating valid signal readings as failures.
- Keep saved readings accessible through map and connection failures.

## Accessibility & Inclusion

Outdoor readability, rating names alongside colors, Android touch targets, TalkBack, and larger system text are required by the spec. Browser reference checks do not substitute for native accessibility or physical phone validation.
