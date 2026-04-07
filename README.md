# Q*Bert: Collider Run (Java)

A polished arcade-style Java game inspired by Q*Bert and collider physics.

## Highlights
- Isometric stepped collider board with shaded cubes and animated effects.
- Multiple screen states: title, pause, gameplay, end-of-run overlay.
- Smooth interpolation for player/enemy motion and collision burst particles.
- Three collider tuning systems:
  - **Beam energy**
  - **Luminosity**
  - **Detector calibration**
- Collision tiles produce event outcomes using:
  - random parton energy fraction,
  - calibration modifier,
  - weighted rarity,
  - threshold-gated particle unlocks.
- Discovery board with threshold-inspired progression:
  - μ+μ-, K+K-, p p̄, Z, Higgs, W+W-, t t̄
- Score, lives, event log, enemies, and fail/victory states.

## Controls
- `Enter` = start from title / restart from end screen
- `Q`/`W`/`A`/`S` = isometric hops
- Arrow keys = alternate movement controls
- `P` = pause/unpause
- `R` = restart run

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```

## Design Notes
- The pyramid acts as a discrete energy landscape.
- Heavier signatures require higher effective collision energy.
- Collision outcome quality depends on setup + stochastic parton fraction.
