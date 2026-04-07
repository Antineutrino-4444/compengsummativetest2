# Q*Bert: Collider Run (Java)

A polished arcade-style Java game inspired by Q*Bert and collider physics.

## Features
- Stepped pyramid board where each hop changes collider settings.
- Three tunable systems:
  - **Beam energy**
  - **Luminosity**
  - **Detector calibration**
- Collision tiles that produce physics events via a parton-fraction mechanic.
- Discovery board with real threshold-inspired unlock progression:
  - μ+μ-, K+K-, p p̄, Z, Higgs, W+W-, t t̄
- Score, lives, run fail/victory states, enemy hazards, restart flow.

## Controls
- `Q` = up-left
- `W` = up-right
- `A` = down-left
- `S` = down-right
- `R` = reset run

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```

## Design Notes
- The board maps to energy-state progression naturally.
- Collision outcomes depend on beam setup + random parton fraction.
- Heavier signatures require higher effective collision energy.
