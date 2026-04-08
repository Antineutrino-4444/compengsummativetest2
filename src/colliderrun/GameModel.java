package colliderrun;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GameModel {
    public static final int ROWS = 7;

    private final Random random = new Random();
    private final CollisionEngine collisionEngine = new CollisionEngine(new ParticleDatabase(), random);

    public final BoardTile[][] board = new BoardTile[ROWS][];
    public final Actor player = new Actor(0, 0);
    public final List<Actor> enemies = new ArrayList<>();
    public final Set<ParticleType> discoveries = new LinkedHashSet<>();

    public double beamEnergy = 25;
    public double magnetFocus = 45;
    public double luminosity = 30;
    public double detectorCalibration = 50;
    public double heat = 8;

    public double trackerBuild = 20;
    public double calorimeterBuild = 20;
    public double muonBuild = 20;

    public int score;
    public int lives = 4;
    public int targetIndex;

    public boolean gameOver;
    public boolean victory;
    public boolean tunnelMode;

    public String eventLog = "Step on stations, avoid enemies, then run chamber tunnel and inspect detector tracks.";

    public boolean lastCollisionTriggered;
    public boolean lastCollisionSuccess;
    public double lastEffectiveEnergy;
    public double lastPartonFraction;
    public ParticleType lastParticle;

    private long enemyMoveMs;

    public GameModel() {
        resetBoard();
        spawnEnemies();
    }

    public void tick(long deltaMs) {
        if (gameOver || victory || tunnelMode) return;

        lastCollisionTriggered = false;

        beamEnergy = clamp(beamEnergy - 0.002 * deltaMs, 0, 450);
        luminosity = clamp(luminosity - 0.0015 * deltaMs, 0, 100);
        detectorCalibration = clamp(detectorCalibration - 0.001 * deltaMs, 0, 100);
        magnetFocus = clamp(magnetFocus - 0.0011 * deltaMs, 0, 100);
        heat = clamp(heat + 0.0009 * deltaMs, 0, 120);

        enemyMoveMs += deltaMs;
        if (enemyMoveMs >= 760) {
            enemyMoveMs = 0;
            moveEnemies();
            checkEnemyContact();
        }

        if (heat > 98) {
            lives = Math.max(0, lives - 1);
            heat = 68;
            eventLog = "Magnet quench: one life lost. Use cooling stations before chamber run.";
        }

        if (lives <= 0) gameOver = true;
    }

    public void movePlayer(int dr, int dc) {
        if (gameOver || victory || tunnelMode) return;

        int nr = player.row + dr;
        int nc = player.col + dc;
        if (!isValid(nr, nc)) {
            eventLog = "Invalid move. Stay on platform grid.";
            return;
        }

        player.row = nr;
        player.col = nc;
        BoardTile tile = board[nr][nc];
        tile.visited = true;
        applyTile(tile.type);
        checkEnemyContact();
    }

    public boolean beginCollisionSequence() {
        if (gameOver || victory || tunnelMode) return false;
        BoardTile tile = board[player.row][player.col];
        if (tile.type != TileType.CHAMBER) {
            eventLog = "Move to CHAMBER platform first.";
            return false;
        }
        tunnelMode = true;
        eventLog = "Chamber run started: guide beam packet through tunnel gates.";
        return true;
    }

    public void finishCollisionSequence(double steeringQuality) {
        if (!tunnelMode) return;
        tunnelMode = false;

        ParticleType target = currentTarget();
        double buildFactor = 0.6 + 0.4 * ((trackerBuild + calorimeterBuild + muonBuild) / 300.0);

        CollisionOutcome outcome = collisionEngine.resolve(
                beamEnergy,
                magnetFocus,
                detectorCalibration * buildFactor,
                luminosity,
                heat,
                steeringQuality,
                target
        );

        lastCollisionTriggered = true;
        lastCollisionSuccess = outcome.valid();
        lastEffectiveEnergy = outcome.effectiveEnergy();
        lastPartonFraction = outcome.partonFraction();
        lastParticle = outcome.particle();

        heat = clamp(heat + 12, 0, 120);
        luminosity = clamp(luminosity - 6, 0, 100);
        score += outcome.scoreDelta();

        if (!outcome.valid() || outcome.particle() == null) {
            lives = Math.max(0, lives - 1);
            eventLog = outcome.message() + " Collision failed to produce target-quality signal.";
        } else {
            discoveries.add(outcome.particle());
            if (outcome.particle().ordinal() >= targetIndex) {
                targetIndex++;
                score += 300;
                eventLog = outcome.message() + " Target milestone completed.";
            } else {
                eventLog = outcome.message() + " Valid event, but target not reached yet.";
            }
        }

        if (targetIndex >= ParticleType.values().length) {
            victory = true;
            eventLog = "Campaign complete: detector confirmed all target signatures.";
        }

        if (lives <= 0) gameOver = true;
    }

    private void applyTile(TileType type) {
        switch (type) {
            case INJECTOR -> {
                beamEnergy = clamp(beamEnergy + 22, 0, 450);
                heat = clamp(heat + 6, 0, 120);
                score += 12;
                eventLog = "Injector platform: beam energy increased.";
            }
            case MAGNET -> {
                magnetFocus = clamp(magnetFocus + 14, 0, 100);
                heat = clamp(heat + 3, 0, 120);
                score += 10;
                eventLog = "Magnet platform: beam focus improved.";
            }
            case LUMINOSITY -> {
                luminosity = clamp(luminosity + 15, 0, 100);
                score += 10;
                eventLog = "Luminosity platform: bunch intensity increased.";
            }
            case DETECTOR -> {
                detectorCalibration = clamp(detectorCalibration + 10, 0, 100);
                int channel = random.nextInt(3);
                if (channel == 0) trackerBuild = clamp(trackerBuild + 14, 0, 100);
                else if (channel == 1) calorimeterBuild = clamp(calorimeterBuild + 14, 0, 100);
                else muonBuild = clamp(muonBuild + 14, 0, 100);
                score += 14;
                eventLog = "Detector platform: upgraded one detector subsystem.";
            }
            case COOLING -> {
                heat = clamp(heat - 16, 0, 120);
                score += 9;
                eventLog = "Cooling platform: magnet temperature reduced.";
            }
            case CHAMBER -> eventLog = "Chamber ready. Press SPACE to start chamber run.";
        }
    }

    private void moveEnemies() {
        int[][] moves = {{1, 0}, {1, 1}, {-1, 0}, {-1, -1}};
        for (Actor e : enemies) {
            int[] m = moves[random.nextInt(moves.length)];
            int nr = e.row + m[0];
            int nc = e.col + m[1];
            if (isValid(nr, nc) && board[nr][nc].type != TileType.CHAMBER) {
                e.row = nr;
                e.col = nc;
            }
        }
    }

    private void checkEnemyContact() {
        for (Actor e : enemies) {
            if (e.row == player.row && e.col == player.col) {
                lives = Math.max(0, lives - 1);
                player.row = 0;
                player.col = 0;
                eventLog = "Enemy drone hit! Lost one life and returned to start.";
                return;
            }
        }
    }

    private void spawnEnemies() {
        enemies.clear();
        enemies.add(new Actor(ROWS - 1, 0));
        enemies.add(new Actor(ROWS - 2, ROWS - 2));
        enemies.add(new Actor(ROWS - 3, 1));
    }

    private void resetBoard() {
        for (int r = 0; r < ROWS; r++) {
            board[r] = new BoardTile[r + 1];
            for (int c = 0; c <= r; c++) {
                TileType t;
                if (r == ROWS - 1 && c == ROWS / 2) t = TileType.CHAMBER;
                else {
                    int pattern = (r + c) % 5;
                    t = switch (pattern) {
                        case 0 -> TileType.INJECTOR;
                        case 1 -> TileType.MAGNET;
                        case 2 -> TileType.LUMINOSITY;
                        case 3 -> TileType.DETECTOR;
                        default -> TileType.COOLING;
                    };
                }
                board[r][c] = new BoardTile(r, c, t);
            }
        }
    }

    public ParticleType currentTarget() {
        return ParticleType.values()[Math.min(targetIndex, ParticleType.values().length - 1)];
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col <= row;
    }

    public void reset() {
        beamEnergy = 25;
        magnetFocus = 45;
        luminosity = 30;
        detectorCalibration = 50;
        heat = 8;

        trackerBuild = 20;
        calorimeterBuild = 20;
        muonBuild = 20;

        score = 0;
        lives = 4;
        targetIndex = 0;
        gameOver = false;
        victory = false;
        tunnelMode = false;
        discoveries.clear();
        player.row = 0;
        player.col = 0;
        eventLog = "Step on stations, avoid enemies, then run chamber tunnel and inspect detector tracks.";
        lastCollisionTriggered = false;
        lastParticle = null;
        enemyMoveMs = 0;
        resetBoard();
        spawnEnemies();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
