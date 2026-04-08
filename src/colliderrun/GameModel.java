package colliderrun;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GameModel {
    public enum Phase { TITLE, TUNING, RUN, RESULTS }

    public static class FallingEvent {
        public double x;
        public double y;
        public double speed;
        public boolean signalLike;
        public double baseEnergy;

        public FallingEvent(double x, double speed, boolean signalLike, double baseEnergy) {
            this.x = x;
            this.speed = speed;
            this.signalLike = signalLike;
            this.baseEnergy = baseEnergy;
        }
    }

    private final Random random = new Random();

    public Phase phase = Phase.TITLE;
    public String status = "Press ENTER to start Collider Run.";

    public final int tuningRows = 4;
    public final boolean[][] tuned = new boolean[tuningRows][];
    public final Actor tuner = new Actor(0, 0);
    public final List<Actor> enemies = new ArrayList<>();
    public int tunedCount;
    public int tunedTarget;
    public long tuningMsLeft;
    private long enemyMoveMs;

    public double beamEnergy = 40;
    public double luminosity = 35;
    public double magnetAlignment = 45;

    public long runMsLeft;
    public double detectorX = 0.5;
    public final List<FallingEvent> events = new ArrayList<>();
    public long spawnMs;

    public int trackerLevel = 1;
    public int caloLevel = 1;
    public int muonLevel = 1;
    public int resources;
    public int lives;
    public int score;

    public final Set<ParticleType> discovered = new LinkedHashSet<>();
    public ParticleType lastProduced;
    public double lastEffectiveEnergy;

    public GameModel() {
        for (int r = 0; r < tuningRows; r++) tuned[r] = new boolean[r + 1];
        enemies.add(new Actor(tuningRows - 1, 0));
        enemies.add(new Actor(tuningRows - 1, tuningRows - 1));
    }

    public void startNewGame() {
        phase = Phase.TUNING;
        status = "Stage 1: tune platforms with QEAD (minor phase).";

        for (int r = 0; r < tuningRows; r++) {
            for (int c = 0; c <= r; c++) tuned[r][c] = false;
        }

        tuner.row = 0;
        tuner.col = 0;
        enemies.get(0).row = tuningRows - 1;
        enemies.get(0).col = 0;
        enemies.get(1).row = tuningRows - 1;
        enemies.get(1).col = tuningRows - 1;

        tunedCount = 0;
        tunedTarget = 7;
        tuningMsLeft = 26_000;
        enemyMoveMs = 0;

        beamEnergy = 40;
        luminosity = 35;
        magnetAlignment = 45;

        runMsLeft = 130_000;
        detectorX = 0.5;
        events.clear();
        spawnMs = 0;

        trackerLevel = 1;
        caloLevel = 1;
        muonLevel = 1;
        resources = 0;
        lives = 5;
        score = 0;

        discovered.clear();
        lastProduced = null;
        lastEffectiveEnergy = 0;
    }

    public void tick(long dt) {
        if (phase == Phase.TUNING) tickTuning(dt);
        if (phase == Phase.RUN) tickRun(dt);
    }

    private void tickTuning(long dt) {
        tuningMsLeft = Math.max(0, tuningMsLeft - dt);
        enemyMoveMs += dt;
        if (enemyMoveMs >= 680) {
            enemyMoveMs = 0;
            moveEnemies();
            checkEnemyCollision();
        }
        if (tuningMsLeft == 0 || tunedCount >= tunedTarget || lives <= 0) {
            if (lives <= 0) {
                phase = Phase.RESULTS;
                status = "Failed during tuning.";
            } else {
                phase = Phase.RUN;
                status = "Stage 2: trigger collisions, build detector, and discover particles.";
            }
        }
    }

    private void tickRun(long dt) {
        runMsLeft = Math.max(0, runMsLeft - dt);
        spawnMs += dt;
        if (spawnMs >= 620) {
            spawnMs = 0;
            spawnEvent();
        }

        List<FallingEvent> remove = new ArrayList<>();
        for (FallingEvent e : events) {
            e.y += e.speed * dt * 0.001;
            if (e.y > 1.10) {
                if (e.signalLike) lives = Math.max(0, lives - 1);
                remove.add(e);
            }
        }
        events.removeAll(remove);

        if (lives <= 0 || runMsLeft == 0) {
            phase = Phase.RESULTS;
            status = "Run complete. Press ENTER to restart.";
        }
    }

    private void spawnEvent() {
        boolean signal = random.nextDouble() < signalChance();
        double x = 0.1 + random.nextDouble() * 0.8;
        double speed = 0.21 + random.nextDouble() * 0.34;
        double base = signal ? 60 + random.nextDouble() * 360 : 10 + random.nextDouble() * 120;
        events.add(new FallingEvent(x, speed, signal, base));
    }

    private double signalChance() {
        double detectorQuality = (trackerLevel + caloLevel + muonLevel) / 18.0;
        return 0.22 + detectorQuality * 0.12;
    }

    public void moveTuner(int dr, int dc) {
        if (phase != Phase.TUNING) return;
        int nr = tuner.row + dr;
        int nc = tuner.col + dc;
        if (nr < 0 || nr >= tuningRows || nc < 0 || nc > nr) return;

        tuner.row = nr;
        tuner.col = nc;

        if (!tuned[nr][nc]) {
            tuned[nr][nc] = true;
            tunedCount++;
            int mode = (nr + nc) % 3;
            if (mode == 0) beamEnergy = clamp(beamEnergy + 18, 0, 700);
            if (mode == 1) luminosity = clamp(luminosity + 12, 0, 100);
            if (mode == 2) magnetAlignment = clamp(magnetAlignment + 14, 0, 100);
            score += 30;
            status = "Tuned node " + tunedCount + "/" + tunedTarget;
        }
        checkEnemyCollision();
    }

    public void moveDetector(double dx) {
        if (phase != Phase.RUN) return;
        detectorX = clamp(detectorX + dx, 0.05, 0.95);
    }

    public void trigger() {
        if (phase != Phase.RUN) return;
        FallingEvent best = null;
        double bestDist = 999;
        for (FallingEvent e : events) {
            if (e.y < 0.66 || e.y > 0.92) continue;
            double d = Math.abs(e.x - detectorX);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }

        if (best == null) {
            score -= 6;
            status = "No event in trigger window.";
            return;
        }

        events.remove(best);
        double acceptance = 0.10 + trackerLevel * 0.03;
        if (bestDist > acceptance) {
            score -= 12;
            status = "Missed the event trajectory.";
            return;
        }

        if (!best.signalLike) {
            score -= 16;
            status = "Background accepted. Tune detector levels.";
            return;
        }

        score += 45;
        resources += 1;
        produceParticle(best.baseEnergy);
    }

    private void produceParticle(double baseEnergy) {
        double parton = 0.22 + random.nextDouble() * 0.78;
        double machine = (beamEnergy / 700.0) * (0.7 + luminosity / 120.0) * (0.7 + magnetAlignment / 120.0);
        double detector = 0.75 + ((trackerLevel + caloLevel + muonLevel) / 18.0) * 0.55;
        lastEffectiveEnergy = baseEnergy * parton * machine * detector;

        ParticleType produced = null;
        for (ParticleType p : ParticleType.values()) {
            if (lastEffectiveEnergy >= p.thresholdGeV) produced = p;
        }

        if (produced == null) {
            status = "Soft event only (" + fmt(lastEffectiveEnergy) + " GeV).";
            return;
        }

        lastProduced = produced;
        discovered.add(produced);
        score += 110 + produced.ordinal() * 45;
        status = "Produced " + produced.label + " at " + fmt(lastEffectiveEnergy) + " GeV";
    }

    public void upgradeTracker() {
        if (phase != Phase.RUN || resources < 2 || trackerLevel >= 6) return;
        resources -= 2;
        trackerLevel++;
    }

    public void upgradeCalo() {
        if (phase != Phase.RUN || resources < 2 || caloLevel >= 6) return;
        resources -= 2;
        caloLevel++;
    }

    public void upgradeMuon() {
        if (phase != Phase.RUN || resources < 2 || muonLevel >= 6) return;
        resources -= 2;
        muonLevel++;
    }

    private void moveEnemies() {
        int[][] moves = {{1, 0}, {1, 1}, {-1, 0}, {-1, -1}};
        for (Actor enemy : enemies) {
            int[] m = moves[random.nextInt(moves.length)];
            int nr = enemy.row + m[0];
            int nc = enemy.col + m[1];
            if (nr >= 0 && nr < tuningRows && nc >= 0 && nc <= nr) {
                enemy.row = nr;
                enemy.col = nc;
            }
        }
    }

    private void checkEnemyCollision() {
        for (Actor enemy : enemies) {
            if (enemy.row == tuner.row && enemy.col == tuner.col) {
                lives = Math.max(0, lives - 1);
                tuner.row = 0;
                tuner.col = 0;
                status = "Drone collision in tuning stage. Lives: " + lives;
                return;
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String fmt(double d) {
        return String.format("%.1f", d);
    }
}
