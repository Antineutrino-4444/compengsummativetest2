# Qbert Collider Run (Major Rebuild)

This version is a full redesign aimed at a polished arcade feel.

## Core Design
- **Stage 1 (minor, 10-20%)**: QEAD platform tuning on a compact stepped grid.
  - charge nodes
  - avoid drones
  - boosts initial collider parameters
- **Stage 2 (main, 80-90%)**: real-time collider operations.
  - falling event stream (signal/background)
  - move detector trigger window with A/D
  - trigger with Space/Enter
  - spend resources on Tracker/Calo/Muon upgrades (`1/2/3`)
  - produce particles based on effective collision energy and detector quality

## Science Mechanics Used
- beam energy, luminosity, and alignment affect effective collision energy
- per-collision random parton fraction
- detector subsystem upgrades improve reconstruction quality
- particles unlock by threshold energy (via `ParticleType` thresholds)

## Controls
- `Q` `E` `A` `D` in stage 1 (platform movement)
- `A` `D` in stage 2 (trigger window movement)
- `Space` or `Enter` to trigger event in stage 2
- `1` `2` `3` for tracker/calo/muon upgrades
- `Enter` to start/restart

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```
