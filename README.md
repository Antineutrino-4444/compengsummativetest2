# Q*Bert: Collider Run (Java)

A polished Java arcade game where you run a particle-collider campaign by stepping across an isometric operations lattice.

## Core Loop (now explicit)
1. Move across module tiles to tune machine parameters.
2. Build a good collider state: energy, magnet focus, luminosity, detector calibration, and heat control.
3. Reach the **collision chamber** tile.
4. Press **SPACE** to fire a proton-proton collision event.
5. Validate target signatures in increasing mass order (μ+μ- → t t̄).

## Tile Systems
- **Injector**: raises beam energy quickly but adds heat.
- **Magnet**: improves beam focus.
- **Luminosity**: raises event rate.
- **Detector**: improves calibration quality.
- **Cooling**: drops heat and stabilizes operation.
- **Chamber**: required tile for firing collisions.

## Win / Lose
- Win by completing all target signatures in sequence.
- Lose if lives run out (bad collisions / quenches).

## Controls
- `Enter` = start/restart from title/end
- `Q`/`W`/`A`/`S` or arrows = move on the lattice
- `Space` = trigger collision (only works on chamber tile)
- `P` = pause
- `R` = reset run

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```
