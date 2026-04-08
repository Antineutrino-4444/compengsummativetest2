package colliderrun;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public class GameModel {
    public static final int ROWS = 7;

    private final Random random = new Random();
    private final CollisionEngine collisionEngine = new CollisionEngine(new ParticleDatabase(), random);

    public final BoardTile[][] board = new BoardTile[ROWS][];
    public final Actor player = new Actor(0, 0);
    public final Set<ParticleType> discoveries = new LinkedHashSet<>();

    public double beamEnergy = 20;
    public double magnetFocus = 35;
    public double luminosity = 20;
    public double detectorCalibration = 45;
    public double heat = 10;

    public int score;
    public int lives = 3;
    public int targetIndex;

    public boolean gameOver;
    public boolean victory;
    public boolean tunnelMode;

    public String eventLog = "Goal: tune machine, reach CHAMBER, run beam tunnel, confirm target particle.";

    public boolean lastCollisionTriggered;
    public boolean lastCollisionSuccess;
    public double lastEffectiveEnergy;
    public double lastPartonFraction;

    public GameModel() {
        resetBoard();
    }

    public void tick(long deltaMs) {
        if (gameOver || victory || tunnelMode) return;

        lastCollisionTriggered = false;

        beamEnergy = clamp(beamEnergy - 0.0035 * deltaMs, 0, 450);
        luminosity = clamp(luminosity - 0.0025 * deltaMs, 0, 100);
        detectorCalibration = clamp(detectorCalibration - 0.0015 * deltaMs, 0, 100);
        magnetFocus = clamp(magnetFocus - 0.0018 * deltaMs, 0, 100);
        heat = clamp(heat + 0.0012 * deltaMs, 0, 120);

        if (heat > 95) {
            lives = Math.max(0, lives - 1);
            heat = 60;
            eventLog = "Magnet quench! Cooling failure cost one life.";
        }

        if (lives <= 0) gameOver = true;
    }

    public void movePlayer(int dr, int dc) {
        if (gameOver || victory || tunnelMode) return;

        int nr = player.row + dr;
        int nc = player.col + dc;
        if (!isValid(nr, nc)) {
            eventLog = "Out of bounds. Stay on lattice.";
            return;
        }

        player.row = nr;
        player.col = nc;

        BoardTile tile = board[nr][nc];
        tile.visited = true;
        applyTile(tile.type);
    }

    public boolean beginCollisionSequence() {
        if (gameOver || victory || tunnelMode) return false;
        BoardTile tile = board[player.row][player.col];
        if (tile.type != TileType.CHAMBER) {
            eventLog = "Move to CHAMBER tile first.";
            return false;
        }
        tunnelMode = true;
        eventLog = "Tunnel phase: steer beam packet with A/D (or Left/Right), survive gates.";
        return true;
    }

    public void finishCollisionSequence(double steeringQuality) {
        if (!tunnelMode) return;
        tunnelMode = false;

        ParticleType target = currentTarget();
        CollisionOutcome outcome = collisionEngine.resolve(
                beamEnergy,
                magnetFocus,
                detectorCalibration,
                luminosity,
                heat,
                steeringQuality,
                target
        );

        lastCollisionTriggered = true;
        lastCollisionSuccess = outcome.valid();
        lastEffectiveEnergy = outcome.effectiveEnergy();
        lastPartonFraction = outcome.partonFraction();

        heat = clamp(heat + 15, 0, 120);
        luminosity = clamp(luminosity - 8, 0, 100);
        score += outcome.scoreDelta();

        if (!outcome.valid() || outcome.particle() == null) {
            lives = Math.max(0, lives - 1);
            eventLog = outcome.message() + " Life lost.";
        } else {
            discoveries.add(outcome.particle());
            if (outcome.particle().ordinal() >= targetIndex) {
                targetIndex++;
                score += 400;
                eventLog = outcome.message() + " TARGET COMPLETE.";
            } else {
                eventLog = outcome.message() + " Not enough for current target.";
            }
        }

        if (targetIndex >= ParticleType.values().length) {
            victory = true;
            eventLog = "Campaign complete: all target particles confirmed.";
        }

        if (lives <= 0) gameOver = true;
    }

    private void applyTile(TileType type) {
        switch (type) {
            case INJECTOR -> {
                beamEnergy = clamp(beamEnergy + 28, 0, 450);
                heat = clamp(heat + 7, 0, 120);
                score += 16;
                eventLog = "Injector raised beam to " + fmt(beamEnergy) + " GeV.";
            }
            case MAGNET -> {
                magnetFocus = clamp(magnetFocus + 16, 0, 100);
                heat = clamp(heat + 4, 0, 120);
                score += 14;
                eventLog = "Magnet focus now " + fmt(magnetFocus) + "%";
            }
            case LUMINOSITY -> {
                luminosity = clamp(luminosity + 18, 0, 100);
                score += 12;
                eventLog = "Luminosity now " + fmt(luminosity) + "%";
            }
            case DETECTOR -> {
                detectorCalibration = clamp(detectorCalibration + 17, 0, 100);
                heat = clamp(heat - 3, 0, 120);
                score += 13;
                eventLog = "Detector calibration " + fmt(detectorCalibration) + "%";
            }
            case COOLING -> {
                heat = clamp(heat - 18, 0, 120);
                magnetFocus = clamp(magnetFocus + 5, 0, 100);
                score += 10;
                eventLog = "Cooling active. Heat " + fmt(heat) + "%";
            }
            case CHAMBER -> eventLog = "At CHAMBER. Press SPACE to start tunnel phase.";
        }

        if (type != TileType.CHAMBER) {
            maybeRerollTile();
        }
    }

    private void maybeRerollTile() {
        if (random.nextDouble() < 0.2) {
            int r = 1 + random.nextInt(ROWS - 1);
            int c = random.nextInt(r + 1);
            if (board[r][c].type != TileType.CHAMBER) {
                board[r][c].type = rollTileType();
                board[r][c].visited = false;
            }
        }
    }

    private void resetBoard() {
        for (int r = 0; r < ROWS; r++) {
            board[r] = new BoardTile[r + 1];
            for (int c = 0; c <= r; c++) {
                board[r][c] = new BoardTile(r, c, rollTileType());
            }
        }
        board[0][0].type = TileType.INJECTOR;
        board[ROWS - 1][ROWS / 2].type = TileType.CHAMBER;
    }

    public ParticleType currentTarget() {
        return ParticleType.values()[Math.min(targetIndex, ParticleType.values().length - 1)];
    }

    private TileType rollTileType() {
        int x = random.nextInt(100);
        if (x < 24) return TileType.INJECTOR;
        if (x < 45) return TileType.MAGNET;
        if (x < 64) return TileType.LUMINOSITY;
        if (x < 83) return TileType.DETECTOR;
        if (x < 97) return TileType.COOLING;
        return TileType.CHAMBER;
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col <= row;
    }

    public void reset() {
        beamEnergy = 20;
        magnetFocus = 35;
        luminosity = 20;
        detectorCalibration = 45;
        heat = 10;
        score = 0;
        lives = 3;
        targetIndex = 0;
        gameOver = false;
        victory = false;
        tunnelMode = false;
        discoveries.clear();
        player.row = 0;
        player.col = 0;
        eventLog = "Goal: tune machine, reach CHAMBER, run beam tunnel, confirm target particle.";
        lastCollisionTriggered = false;
        resetBoard();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
