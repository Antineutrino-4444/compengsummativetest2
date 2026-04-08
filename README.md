# Qbert Collider Run (Complete Overhaul)

This version is rebuilt from scratch.

## Game Structure
- **Stage 1 (short): Platform Charge**
  - QEAD movement on a compact stepped board
  - charge platforms by stepping on them
  - avoid moving enemy drones
- **Stage 2 (main): Detector Event Classification**
  - animated detector tracks are generated per event class
  - classify events using LEFT/RIGHT + ENTER
  - hover track endpoints to inspect track details

## Controls
- `Q` = up-left
- `E` = up-right
- `A` = down-left
- `D` = down-right
- `LEFT/RIGHT` = change selected event class (stage 2)
- `ENTER` = start run / submit classification / replay

## Window
- Fixed size and non-resizable (set in `Main`) to prevent clipping.

## Run
```bash
javac -d out src/colliderrun/*.java
java -cp out colliderrun.Main
```
