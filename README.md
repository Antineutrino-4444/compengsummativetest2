# Qbert Collider Run (Java)

A Java arcade game with two linked gameplay phases:
- **Phase 1 (Board Ops):** move across modules to tune collider systems.
- **Phase 2 (Tunnel Run):** after reaching the chamber, steer a beam packet through gates to determine collision quality.

## Clear Objective
Repeat this cycle until all targets are confirmed in order:
1. Improve machine state (Beam, Focus, Luminosity, Detector, Heat).
2. Move to **CHAMBER** tile.
3. Press **Space** to enter tunnel phase.
4. Survive tunnel gates (A/D or Left/Right).
5. Collision result is computed and may complete current target.

## Tile Types
- Injector: +Beam, +Heat
- Magnet: +Focus
- Luminosity: +Lumi
- Detector: +Detector quality
- Cooling: -Heat
- Chamber: starts tunnel + collision sequence

## Controls
- Enter: start / restart from title or game over
- Q/W/A/S or Arrow keys: board movement
- Space: start tunnel phase (only on Chamber tile)
- A/D or Left/Right during tunnel: steer beam packet
- P: pause
- R: reset

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```
