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

    public double beamEnergy = 15.0;
    public double luminosity = 12.0;
    public double calibration = 40.0;
    public int score = 0;
    public int lives = 3;
    public boolean gameOver;
    public boolean victory;
    public String eventLog = "Tune the beamline and trigger a discovery.";
    public boolean lastCollisionTriggered;
    public boolean lastCollisionSuccess;
    public double lastPartonFraction;
    public double lastEffectiveEnergy;

    private long enemyMoveAccumulatorMs;

    public GameModel() {
        resetBoard();
        spawnEnemies();
    }

    public void tick(long deltaMs) {
        if (gameOver || victory) {
            return;
        }
        lastCollisionTriggered = false;
        beamEnergy = clamp(beamEnergy - 0.004 * deltaMs, 0, 420);
        luminosity = clamp(luminosity - 0.003 * deltaMs, 0, 200);
        calibration = clamp(calibration - 0.002 * deltaMs, 0, 100);

        enemyMoveAccumulatorMs += deltaMs;
        if (enemyMoveAccumulatorMs >= 700) {
            enemyMoveAccumulatorMs = 0;
            moveEnemies();
            checkEnemyHit();
        }
        if (lives <= 0) {
            gameOver = true;
        }
    }

    public void movePlayer(int dr, int dc) {
        if (gameOver || victory) {
            return;
        }
        int nr = player.row + dr;
        int nc = player.col + dc;
        if (!isValid(nr, nc)) {
            eventLog = "That jump misses the collider lattice.";
            return;
        }

        player.row = nr;
        player.col = nc;
        onPlayerLanded(board[nr][nc]);
        checkEnemyHit();
        if (discoveries.size() == ParticleType.values().length) {
            victory = true;
            eventLog = "All signatures confirmed! Collider run complete.";
        }
    }

    private void onPlayerLanded(BoardTile tile) {
        tile.visited = true;
        switch (tile.type) {
            case ENERGY -> {
                beamEnergy = clamp(beamEnergy + 20 + random.nextInt(18), 0, 420);
                score += 18;
                eventLog = "Beam energy ramped to " + fmt(beamEnergy) + " GeV.";
            }
            case LUMINOSITY -> {
                luminosity = clamp(luminosity + 12 + random.nextInt(10), 0, 200);
                score += 15;
                eventLog = "Luminosity boosted to " + fmt(luminosity) + ".";
            }
            case CALIBRATION -> {
                calibration = clamp(calibration + 11 + random.nextInt(12), 0, 100);
                score += 14;
                eventLog = "Detector calibration now " + fmt(calibration) + "%";
            }
            case COLLISION -> {
                CollisionOutcome outcome = collisionEngine.trigger(beamEnergy, calibration, luminosity);
                score += outcome.scoreDelta();
                lastCollisionTriggered = true;
                lastCollisionSuccess = outcome.valid();
                lastPartonFraction = outcome.partonFraction();
                lastEffectiveEnergy = outcome.effectiveEnergy();
                if (outcome.valid() && outcome.particle() != null) {
                    discoveries.add(outcome.particle());
                } else {
                    lives -= 1;
                }
                eventLog = outcome.message() + " (x=" + fmt(outcome.partonFraction()) + ", Eeff="
                        + fmt(outcome.effectiveEnergy()) + " GeV)";
            }
        }

        if (tile.type != TileType.COLLISION) {
            tile.type = rollTileType();
        }
    }

    private TileType rollTileType() {
        int r = random.nextInt(100);
        if (r < 36) return TileType.ENERGY;
        if (r < 64) return TileType.LUMINOSITY;
        if (r < 89) return TileType.CALIBRATION;
        return TileType.COLLISION;
    }

    private void spawnEnemies() {
        enemies.clear();
        enemies.add(new Actor(ROWS - 1, 0));
        enemies.add(new Actor(ROWS - 1, ROWS - 1));
        enemies.add(new Actor(ROWS - 2, (ROWS - 2) / 2));
    }

    private void moveEnemies() {
        int[][] moves = {{1, 1}, {1, 0}, {-1, -1}, {-1, 0}};
        for (Actor enemy : enemies) {
            int[] m = moves[random.nextInt(moves.length)];
            int nr = enemy.row + m[0];
            int nc = enemy.col + m[1];
            if (isValid(nr, nc)) {
                enemy.row = nr;
                enemy.col = nc;
            }
        }
    }

    private void checkEnemyHit() {
        for (Actor enemy : enemies) {
            if (enemy.row == player.row && enemy.col == player.col) {
                lives -= 1;
                eventLog = "Beamline sabotage! Lost a life.";
                player.row = 0;
                player.col = 0;
                return;
            }
        }
    }

    public void reset() {
        beamEnergy = 15;
        luminosity = 12;
        calibration = 40;
        score = 0;
        lives = 3;
        gameOver = false;
        victory = false;
        eventLog = "New run initialized.";
        discoveries.clear();
        player.row = 0;
        player.col = 0;
        lastCollisionTriggered = false;
        resetBoard();
        spawnEnemies();
    }

    private void resetBoard() {
        for (int r = 0; r < ROWS; r++) {
            board[r] = new BoardTile[r + 1];
            for (int c = 0; c <= r; c++) {
                board[r][c] = new BoardTile(r, c, rollTileType());
            }
        }
        board[0][0].type = TileType.ENERGY;
        board[ROWS - 1][ROWS / 2].type = TileType.COLLISION;
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col <= row;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
