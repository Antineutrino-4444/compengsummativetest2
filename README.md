# Qbert Collider Run (Java)

A Java arcade game with three clearly separated stages per collision attempt.

## Gameplay Stages
1. **Station Grid (re-imagined Qbert part)**
   - Move across stations to tune Beam, Focus, Luminosity, Detector, and Heat.
   - Reach the **CHAMBER** station.
2. **Tunnel Run (major gameplay stage)**
   - Press **Space** at CHAMBER to start a long beam-tunnel run.
   - Steer with **A/D** or **Left/Right** through many gates.
   - Gate performance determines collision quality.
3. **Detector Replay**
   - After each collision, animated detector tracks are shown in a replay overlay.
   - Replay intensity reflects event quality/particle outcome.

## Goal
Complete target particles in order while managing lives and machine state.

## Controls
- Enter: start/restart
- Q/W/A/S or arrows: move on station grid
- Space: start tunnel run from CHAMBER
- A/D or Left/Right in tunnel: steer beam packet
- P: pause
- R: reset

## Window Support
- Designed for **1100x760 or larger**.
- The app enforces a minimum size and shows a warning if smaller.

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```
