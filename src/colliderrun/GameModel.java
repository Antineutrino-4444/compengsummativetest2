package colliderrun;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameModel {
    public enum Phase { TITLE, CALIBRATION, CLASSIFICATION, RESULTS }
    public enum EventClass { MUON_PAIR, JET_BURST, MISSING_ENERGY, BOSON_CANDIDATE }

    public static class EventTrack {
        public final double angle;
        public final double length;
        public final int charge;
        public final EventClass flavor;

        public EventTrack(double angle, double length, int charge, EventClass flavor) {
            this.angle = angle;
            this.length = length;
            this.charge = charge;
            this.flavor = flavor;
        }
    }

    private final Random random = new Random();

    public final int rows = 5;
    public final boolean[][] charged = new boolean[rows][];
    public final Actor player = new Actor(0, 0);
    public final List<Actor> enemies = new ArrayList<>();

    public Phase phase = Phase.TITLE;

    public int score;
    public int lives;
    public int chargedCount;
    public int chargedTarget;

    public long calibrationMsLeft;
    public long classificationMsLeft;
    public long eventMsLeft;

    public int trackerPower;
    public int caloPower;
    public int muonPower;

    public EventClass currentEventClass;
    public final List<EventTrack> currentTracks = new ArrayList<>();
    public int correctTags;
    public int totalTags;
    public int selectedLabelIndex;

    public String status = "Press ENTER to begin.";

    private long enemyTick;

    public GameModel() {
        resetBoard();
    }

    public void startRun() {
        resetBoard();
        lives = 3;
        score = 0;
        chargedCount = 0;
        chargedTarget = 8;
        calibrationMsLeft = 40_000;
        classificationMsLeft = 95_000;
        trackerPower = 45;
        caloPower = 45;
        muonPower = 45;
        correctTags = 0;
        totalTags = 0;
        selectedLabelIndex = 0;
        phase = Phase.CALIBRATION;
        status = "Stage 1: Charge 8 platforms, avoid drones (QEAD).";
    }

    public void tick(long dt) {
        if (phase == Phase.CALIBRATION) tickCalibration(dt);
        else if (phase == Phase.CLASSIFICATION) tickClassification(dt);
    }

    private void tickCalibration(long dt) {
        calibrationMsLeft = Math.max(0, calibrationMsLeft - dt);
        enemyTick += dt;
        if (enemyTick > 650) {
            enemyTick = 0;
            moveEnemies();
            checkEnemyCollision();
        }

        if (calibrationMsLeft == 0 || chargedCount >= chargedTarget || lives <= 0) {
            beginClassification();
        }
    }

    private void beginClassification() {
        if (lives <= 0) {
            phase = Phase.RESULTS;
            status = "Run failed in Stage 1.";
            return;
        }
        int bonus = (int) Math.round((chargedCount / (double) chargedTarget) * 30);
        trackerPower = clamp(trackerPower + bonus, 0, 100);
        caloPower = clamp(caloPower + bonus, 0, 100);
        muonPower = clamp(muonPower + bonus, 0, 100);

        phase = Phase.CLASSIFICATION;
        status = "Stage 2: Classify detector events. Arrow keys + ENTER.";
        spawnEvent();
    }

    private void tickClassification(long dt) {
        classificationMsLeft = Math.max(0, classificationMsLeft - dt);
        eventMsLeft = Math.max(0, eventMsLeft - dt);

        if (eventMsLeft == 0) {
            score -= 25;
            totalTags++;
            status = "Missed event window.";
            spawnEvent();
        }

        if (classificationMsLeft == 0) {
            phase = Phase.RESULTS;
            status = "Shift complete.";
        }
    }

    public void moveCalibrationPlayer(int dr, int dc) {
        if (phase != Phase.CALIBRATION) return;
        int nr = player.row + dr;
        int nc = player.col + dc;
        if (!isValid(nr, nc)) return;

        player.row = nr;
        player.col = nc;
        if (!charged[nr][nc]) {
            charged[nr][nc] = true;
            chargedCount++;
            score += 20;
            status = "Platform charged: " + chargedCount + " / " + chargedTarget;
        }
        checkEnemyCollision();
    }

    public void selectPrevLabel() {
        if (phase != Phase.CLASSIFICATION) return;
        selectedLabelIndex = (selectedLabelIndex + EventClass.values().length - 1) % EventClass.values().length;
    }

    public void selectNextLabel() {
        if (phase != Phase.CLASSIFICATION) return;
        selectedLabelIndex = (selectedLabelIndex + 1) % EventClass.values().length;
    }

    public void submitLabel() {
        if (phase != Phase.CLASSIFICATION) return;
        EventClass guess = EventClass.values()[selectedLabelIndex];
        totalTags++;
        if (guess == currentEventClass) {
            correctTags++;
            score += 80;
            status = "Correct classification: " + guess;
        } else {
            score -= 30;
            status = "Incorrect. True class was " + currentEventClass;
        }
        spawnEvent();
    }

    private void spawnEvent() {
        currentEventClass = rollEventClass();
        currentTracks.clear();

        int count = switch (currentEventClass) {
            case MUON_PAIR -> 2;
            case JET_BURST -> 10;
            case MISSING_ENERGY -> 6;
            case BOSON_CANDIDATE -> 4;
        };

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double len = 90 + random.nextDouble() * 160;
            int charge = random.nextBoolean() ? 1 : -1;
            if (currentEventClass == EventClass.MUON_PAIR && i == 1) {
                angle += Math.PI;
                charge = -1;
            }
            if (currentEventClass == EventClass.JET_BURST) len = 60 + random.nextDouble() * 110;
            if (currentEventClass == EventClass.MISSING_ENERGY && i < 2) len = 180 + random.nextDouble() * 80;
            currentTracks.add(new EventTrack(angle, len, charge, currentEventClass));
        }

        eventMsLeft = 6500;
    }

    private EventClass rollEventClass() {
        int r = random.nextInt(100);
        if (r < 30) return EventClass.MUON_PAIR;
        if (r < 60) return EventClass.JET_BURST;
        if (r < 82) return EventClass.MISSING_ENERGY;
        return EventClass.BOSON_CANDIDATE;
    }

    private void moveEnemies() {
        int[][] moves = {{1, 0}, {1, 1}, {-1, 0}, {-1, -1}};
        for (Actor e : enemies) {
            int[] m = moves[random.nextInt(moves.length)];
            int nr = e.row + m[0];
            int nc = e.col + m[1];
            if (isValid(nr, nc)) {
                e.row = nr;
                e.col = nc;
            }
        }
    }

    private void checkEnemyCollision() {
        for (Actor e : enemies) {
            if (e.row == player.row && e.col == player.col) {
                lives = Math.max(0, lives - 1);
                player.row = 0;
                player.col = 0;
                status = "Drone collision! Lives: " + lives;
                return;
            }
        }
    }

    private void resetBoard() {
        for (int r = 0; r < rows; r++) {
            charged[r] = new boolean[r + 1];
        }
        player.row = 0;
        player.col = 0;
        enemies.clear();
        enemies.add(new Actor(rows - 1, 0));
        enemies.add(new Actor(rows - 1, rows - 1));
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col <= row;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
