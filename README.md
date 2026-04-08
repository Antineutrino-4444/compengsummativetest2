# Qbert Collider Run (Java)

## Core Loop
1. **Platform Stage (QEAD controls):** step on platforms to tune collider systems and avoid enemy drones.
2. **Tunnel Stage:** press Space on CHAMBER, then steer beam packet with A/D.
3. **Detector Replay Stage:** view generated tracks and hover over tracks/detector layers for details.

## Controls
- `Q` = up-left platform jump
- `E` = up-right platform jump
- `A` = down-left platform jump (or steer left in tunnel)
- `D` = down-right platform jump (or steer right in tunnel)
- `Space` = start tunnel run from CHAMBER
- `P` = pause
- `R` = reset
- `Enter` = start/restart from title/end

## Notes
- Enemy drones roam the platform stage and cost lives on contact.
- Detector build quality (tracker/calo/muon) is improved via detector platforms and affects event quality.
- Window size is fixed by the app (`1280x860`, non-resizable) to prevent clipping.

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```
