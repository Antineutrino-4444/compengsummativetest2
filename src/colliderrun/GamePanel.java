package colliderrun;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GamePanel extends JPanel {
    private enum ScreenState { TITLE, PLAYING, PAUSED, END }

    private final GameModel model = new GameModel();
    private final Timer timer;
    private final Random random = new Random();

    private final Map<String, Point> boardPoints = new HashMap<>();
    private final List<ParticleFx> particles = new ArrayList<>();
    private final List<Track> detectorTracks = new ArrayList<>();

    private ScreenState state = ScreenState.TITLE;
    private long lastTickNanos = System.nanoTime();
    private double pulse;

    private double playerRenderX;
    private double playerRenderY;
    private double chamberFlash;

    // stage 2 tunnel minigame (major component)
    private boolean tunnelActive;
    private double tunnelX;
    private double tunnelVelocity;
    private int tunnelStep;
    private int tunnelHits;
    private int tunnelMaxSteps;
    private double gateCenter;
    private double gateWidth;
    private double tunnelTick;

    // detector replay animation after collision
    private boolean detectorReplayActive;
    private double detectorReplayMs;

    public GamePanel() {
        setBackground(new Color(7, 10, 20));
        setFocusable(true);
        setupInput();

        timer = new Timer(16, this::onFrame);
        timer.start();
    }

    private void setupInput() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> {
                        if (state == ScreenState.TITLE || state == ScreenState.END) {
                            hardReset();
                            state = ScreenState.PLAYING;
                        }
                    }
                    case KeyEvent.VK_P -> {
                        if (state == ScreenState.PLAYING) state = ScreenState.PAUSED;
                        else if (state == ScreenState.PAUSED) state = ScreenState.PLAYING;
                    }
                    case KeyEvent.VK_R -> {
                        hardReset();
                        state = ScreenState.PLAYING;
                    }
                    case KeyEvent.VK_SPACE -> {
                        if (state == ScreenState.PLAYING && !tunnelActive && !detectorReplayActive) {
                            if (model.beginCollisionSequence()) {
                                startTunnelPhase();
                            }
                        }
                    }
                    case KeyEvent.VK_A, KeyEvent.VK_LEFT -> {
                        if (tunnelActive) tunnelVelocity -= 0.08;
                        else if (state == ScreenState.PLAYING && !detectorReplayActive) model.movePlayer(1, 0);
                    }
                    case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> {
                        if (tunnelActive) tunnelVelocity += 0.08;
                        else if (state == ScreenState.PLAYING && !detectorReplayActive) model.movePlayer(1, 1);
                    }
                    case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> {
                        if (!tunnelActive && state == ScreenState.PLAYING && !detectorReplayActive) model.movePlayer(-1, -1);
                    }
                    case KeyEvent.VK_W, KeyEvent.VK_NUMPAD9, KeyEvent.VK_UP -> {
                        if (!tunnelActive && state == ScreenState.PLAYING && !detectorReplayActive) model.movePlayer(-1, 0);
                    }
                }
                repaint();
            }
        });
    }

    private void hardReset() {
        model.reset();
        particles.clear();
        detectorTracks.clear();
        tunnelActive = false;
        detectorReplayActive = false;
        chamberFlash = 0;
    }

    private void onFrame(ActionEvent ignored) {
        long now = System.nanoTime();
        long dt = (now - lastTickNanos) / 1_000_000;
        lastTickNanos = now;
        pulse += dt * 0.002;

        if (state == ScreenState.PLAYING) {
            if (tunnelActive) {
                tickTunnel(dt);
            } else {
                model.tick(dt);
            }

            if (detectorReplayActive) {
                tickDetectorReplay(dt);
            }

            if (model.gameOver || model.victory) {
                state = ScreenState.END;
                tunnelActive = false;
                detectorReplayActive = false;
            }
        }

        updateLayout();
        animate(dt);
        repaint();
    }

    private void startTunnelPhase() {
        tunnelActive = true;
        tunnelX = 0.5;
        tunnelVelocity = 0;
        tunnelStep = 0;
        tunnelHits = 0;
        tunnelMaxSteps = 22; // easier but major and longer
        gateCenter = 0.5;
        gateWidth = 0.30;
        tunnelTick = 0;
    }

    private void tickTunnel(long dt) {
        tunnelTick += dt;
        tunnelX += tunnelVelocity * dt * 0.0013;
        tunnelVelocity *= 0.95;
        tunnelX = Math.max(0.03, Math.min(0.97, tunnelX));

        if (tunnelTick >= 380) {
            tunnelTick = 0;
            tunnelStep++;
            boolean hit = tunnelX >= gateCenter - gateWidth && tunnelX <= gateCenter + gateWidth;
            if (hit) {
                tunnelHits++;
                spawnTunnelSpark(new Color(120, 255, 170));
            } else {
                spawnTunnelSpark(new Color(255, 120, 120));
            }

            gateCenter = 0.15 + random.nextDouble() * 0.70;
            gateWidth = Math.max(0.16, 0.30 - tunnelStep * 0.005);

            if (tunnelStep >= tunnelMaxSteps) {
                finishTunnelPhase();
            }
        }
    }

    private void finishTunnelPhase() {
        tunnelActive = false;
        double quality = tunnelHits / (double) tunnelMaxSteps;
        model.finishCollisionSequence(quality);

        chamberFlash = model.lastCollisionSuccess ? 1.0 : 0.75;
        spawnCollisionParticles(model.lastCollisionSuccess ? 80 : 40);
        startDetectorReplay();
    }

    private void startDetectorReplay() {
        detectorReplayActive = true;
        detectorReplayMs = 0;
        detectorTracks.clear();

        int trackCount = 10;
        if (model.lastParticle != null) {
            trackCount = 14 + model.lastParticle.ordinal() * 2;
        }

        for (int i = 0; i < trackCount; i++) {
            detectorTracks.add(new Track());
        }
    }

    private void tickDetectorReplay(long dt) {
        detectorReplayMs += dt;
        if (detectorReplayMs > 3600) {
            detectorReplayActive = false;
            detectorTracks.clear();
        }
    }

    private void updateLayout() {
        boardPoints.clear();

        int safeTop = 180;
        int safeBottom = getHeight() - 90;
        int usableHeight = Math.max(260, safeBottom - safeTop);
        int gapY = Math.max(42, usableHeight / (GameModel.ROWS + 3));
        int gapX = (int) (gapY * 1.55);

        int centerX = getWidth() / 2;
        int topY = safeTop;

        for (int r = 0; r < GameModel.ROWS; r++) {
            for (int c = 0; c <= r; c++) {
                int x = centerX + (int) ((c - r * 0.5) * gapX);
                int y = topY + r * gapY;
                boardPoints.put(key(r, c), new Point(x, y));
            }
        }

        if (playerRenderX == 0 && playerRenderY == 0) {
            Point p = boardPoints.get(key(0, 0));
            playerRenderX = p.x;
            playerRenderY = p.y;
        }
    }

    private void animate(long dt) {
        Point p = boardPoints.get(key(model.player.row, model.player.col));
        if (p != null) {
            playerRenderX = lerp(playerRenderX, p.x, 0.26);
            playerRenderY = lerp(playerRenderY, p.y, 0.26);
        }
        chamberFlash = Math.max(0, chamberFlash - dt / 500.0);
        particles.removeIf(px -> !px.step(dt));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);
        drawBoard(g2);
        drawParticles(g2);
        drawHud(g2);

        if (tunnelActive) drawTunnelOverlay(g2);
        if (detectorReplayActive) drawDetectorReplay(g2);

        if (state == ScreenState.TITLE) drawTitle(g2);
        if (state == ScreenState.PAUSED) drawPause(g2);
        if (state == ScreenState.END) drawEnd(g2);

        if (getWidth() < 1100 || getHeight() < 760) {
            drawSizeWarning(g2);
        }

        g2.dispose();
    }

    private void drawBackground(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, new Color(9, 12, 28), 0, h, new Color(4, 6, 12)));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(90, 120, 180, 40));
        for (int i = 0; i < 120; i++) {
            int x = (i * 73) % Math.max(w, 1);
            int y = (i * 43 + 21) % Math.max(h, 1);
            g2.fillOval(x, y, 2, 2);
        }
    }

    private void drawBoard(Graphics2D g2) {
        for (int r = GameModel.ROWS - 1; r >= 0; r--) {
            for (int c = 0; c <= r; c++) {
                Point p = boardPoints.get(key(r, c));
                drawTile(g2, p.x, p.y, model.board[r][c]);
            }
        }
        drawPlayer(g2, (int) playerRenderX, (int) playerRenderY);
    }

    private void drawTile(Graphics2D g2, int x, int y, BoardTile tile) {
        int hw = Math.max(24, getWidth() / 30);
        int hh = Math.max(14, hw / 2);
        int depth = Math.max(12, hh);

        Color top = switch (tile.type) {
            case INJECTOR -> new Color(65, 195, 255);
            case MAGNET -> new Color(170, 150, 255);
            case LUMINOSITY -> new Color(255, 195, 80);
            case DETECTOR -> new Color(140, 235, 165);
            case COOLING -> new Color(120, 220, 240);
            case CHAMBER -> new Color(255, 90, 150);
        };
        if (tile.visited) top = top.brighter();

        Polygon topFace = new Polygon(new int[]{x, x + hw, x, x - hw}, new int[]{y - hh, y, y + hh, y}, 4);
        Polygon leftFace = new Polygon(new int[]{x - hw, x, x, x - hw}, new int[]{y, y + hh, y + hh + depth, y + depth}, 4);
        Polygon rightFace = new Polygon(new int[]{x + hw, x, x, x + hw}, new int[]{y, y + hh, y + hh + depth, y + depth}, 4);

        g2.setColor(top.darker());
        g2.fillPolygon(leftFace);
        g2.setColor(top.brighter());
        g2.fillPolygon(rightFace);
        g2.setColor(top);
        g2.fillPolygon(topFace);

        g2.setColor(new Color(14, 16, 30));
        g2.drawPolygon(topFace);
        g2.drawPolygon(leftFace);
        g2.drawPolygon(rightFace);

        if (tile.type == TileType.CHAMBER && chamberFlash > 0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.45 * chamberFlash)));
            g2.setColor(model.lastCollisionSuccess ? new Color(140, 255, 190) : new Color(255, 110, 110));
            g2.fill(new Ellipse2D.Double(x - 70, y - 70, 140, 140));
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    private void drawPlayer(Graphics2D g2, int x, int y) {
        int bob = (int) (Math.sin(pulse * 5) * 4);
        g2.setColor(new Color(255, 145, 45));
        g2.fillOval(x - 16, y - 54 + bob, 32, 26);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 6, y - 45 + bob, 4, 4);
        g2.fillOval(x + 2, y - 45 + bob, 4, 4);
    }

    private void drawHud(Graphics2D g2) {
        int w = getWidth();
        g2.setColor(new Color(13, 22, 45, 230));
        g2.fillRoundRect(14, 14, w - 28, 146, 16, 16);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString("Collider Run Console", 26, 42);

        meter(g2, 26, 54, 160, "Beam", model.beamEnergy / 450.0, new Color(80, 205, 255));
        meter(g2, 196, 54, 160, "Focus", model.magnetFocus / 100.0, new Color(170, 150, 255));
        meter(g2, 366, 54, 160, "Lumi", model.luminosity / 100.0, new Color(255, 205, 90));
        meter(g2, 536, 54, 160, "Detector", model.detectorCalibration / 100.0, new Color(130, 240, 165));
        meter(g2, 706, 54, 160, "Heat", model.heat / 120.0, new Color(255, 120, 120));

        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(new Color(250, 235, 170));
        g2.drawString("Target: " + model.currentTarget().label + "   Score: " + model.score + "   Lives: " + model.lives, 26, 120);
        g2.setColor(new Color(205, 225, 255));
        g2.drawString("Objective: tune stations -> reach CHAMBER -> SPACE -> tunnel run -> detector replay", 26, 142);

        drawDiscoveryList(g2);
        drawEventLog(g2);
    }

    private void drawTunnelOverlay(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g2.setColor(new Color(5, 10, 24));
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.SrcOver);

        int tunnelW = Math.min(760, w - 140);
        int tunnelH = Math.min(360, h - 220);
        int tx = w / 2 - tunnelW / 2;
        int ty = h / 2 - tunnelH / 2;

        g2.setColor(new Color(18, 28, 58));
        g2.fillRoundRect(tx, ty, tunnelW, tunnelH, 20, 20);
        g2.setColor(new Color(120, 150, 220));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(tx, ty, tunnelW, tunnelH, 20, 20);

        int laneY = ty + tunnelH / 2;
        g2.setColor(new Color(80, 100, 170));
        g2.drawLine(tx + 40, laneY, tx + tunnelW - 40, laneY);

        int gateX = tx + 40 + (int) ((tunnelW - 80) * gateCenter);
        int gateRadius = (int) ((tunnelW - 80) * gateWidth * 0.22);
        g2.setColor(new Color(130, 255, 170));
        g2.drawOval(gateX - gateRadius, laneY - 54, gateRadius * 2, 108);

        int beamX = tx + 40 + (int) ((tunnelW - 80) * tunnelX);
        g2.setColor(new Color(255, 210, 120));
        g2.fillOval(beamX - 10, laneY - 10, 20, 20);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.drawString("Stage 2: Tunnel Run", tx + 20, ty + 36);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.drawString("Steer beam with A/D or Left/Right. Missing gates is allowed; quality just drops.", tx + 20, ty + 62);
        g2.drawString("Hits: " + tunnelHits + " / " + tunnelMaxSteps + "   Step: " + tunnelStep + " / " + tunnelMaxSteps, tx + 20, ty + 86);
    }

    private void drawDetectorReplay(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2 + 20;

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
        g2.setColor(new Color(3, 6, 16));
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.SrcOver);

        g2.setColor(new Color(120, 150, 200));
        g2.setStroke(new BasicStroke(2f));
        for (int r = 70; r <= 260; r += 38) {
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        }

        double progress = Math.min(1.0, detectorReplayMs / 2800.0);
        for (Track t : detectorTracks) {
            t.draw(g2, cx, cy, progress);
        }

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.drawString("Stage 3: Detector Event Replay", cx - 190, 80);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        String label = model.lastParticle == null ? "No clear heavy signature" : model.lastParticle.label;
        g2.drawString("Event: " + label + "   Effective Energy: " + String.format("%.1f", model.lastEffectiveEnergy) + " GeV", cx - 210, 106);
    }

    private void meter(Graphics2D g2, int x, int y, int w, String label, double v, Color c) {
        g2.setColor(new Color(180, 200, 235));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.drawString(label, x, y);

        g2.setColor(new Color(20, 27, 45));
        g2.fillRoundRect(x, y + 6, w, 14, 10, 10);
        int fw = (int) (Math.max(0, Math.min(1, v)) * w);
        g2.setPaint(new GradientPaint(x, y, c.darker(), x + fw, y, c));
        g2.fillRoundRect(x, y + 6, fw, 14, 10, 10);
        g2.setColor(new Color(8, 14, 30));
        g2.drawRoundRect(x, y + 6, w, 14, 10, 10);
    }

    private void drawDiscoveryList(Graphics2D g2) {
        int x = Math.max(20, getWidth() - 300);
        int y = 170;
        g2.setColor(new Color(14, 22, 42, 220));
        g2.fillRoundRect(x, y, 280, 186, 14, 14);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Confirmed events", x + 12, y + 24);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int ty = y + 46;
        for (ParticleType p : ParticleType.values()) {
            boolean found = model.discoveries.contains(p);
            g2.setColor(found ? new Color(130, 255, 150) : new Color(130, 140, 170));
            g2.drawString((found ? "OK " : "-- ") + p.label, x + 12, ty);
            ty += 21;
        }
    }

    private void drawEventLog(Graphics2D g2) {
        int x = 14;
        int y = getHeight() - 54;
        int w = getWidth() - 28;
        g2.setColor(new Color(12, 20, 40, 225));
        g2.fillRoundRect(x, y, w, 38, 10, 10);
        g2.setColor(new Color(225, 240, 255));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString(model.eventLog, x + 10, y + 24);
    }

    private void drawTitle(Graphics2D g2) {
        overlay(g2);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 44));
        g2.drawString("Qbert Collider Run", cx - 210, cy - 90);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Stage 1: Tune station grid", cx - 140, cy - 34);
        g2.drawString("Stage 2: Tunnel run controls collision quality", cx - 220, cy - 6);
        g2.drawString("Stage 3: Detector track replay shows event paths", cx - 225, cy + 22);
        g2.drawString("Press ENTER to start", cx - 98, cy + 66);
    }

    private void drawPause(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 44));
        g2.drawString("Paused", getWidth() / 2 - 84, getHeight() / 2 - 10);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Press P to resume", getWidth() / 2 - 90, getHeight() / 2 + 24);
    }

    private void drawEnd(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 44));
        g2.drawString(model.victory ? "Campaign Complete" : "Run Failed", getWidth() / 2 - 185, getHeight() / 2 - 24);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g2.drawString("Final Score: " + model.score, getWidth() / 2 - 85, getHeight() / 2 + 14);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2.drawString("Press ENTER or R to restart", getWidth() / 2 - 120, getHeight() / 2 + 46);
    }

    private void drawSizeWarning(Graphics2D g2) {
        g2.setColor(new Color(255, 200, 100, 220));
        g2.fillRoundRect(14, getHeight() - 92, 450, 30, 10, 10);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString("Window too small: use at least 1100x760 for full layout.", 24, getHeight() - 72);
    }

    private void overlay(Graphics2D g2) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
        g2.setColor(new Color(4, 8, 20));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private void drawParticles(Graphics2D g2) {
        for (ParticleFx fx : particles) {
            fx.draw(g2);
        }
    }

    private void spawnCollisionParticles(int count) {
        Point chamber = boardPoints.get(key(GameModel.ROWS - 1, GameModel.ROWS / 2));
        if (chamber == null) return;
        Color color = model.lastCollisionSuccess ? new Color(140, 255, 190) : new Color(255, 120, 120);
        for (int i = 0; i < count; i++) {
            particles.add(new ParticleFx(chamber.x, chamber.y, color));
        }
    }

    private void spawnTunnelSpark(Color c) {
        int x = getWidth() / 2;
        int y = getHeight() / 2;
        for (int i = 0; i < 8; i++) {
            particles.add(new ParticleFx(x, y, c));
        }
    }

    private static String key(int r, int c) {
        return r + "," + c;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private class ParticleFx {
        double x;
        double y;
        double vx;
        double vy;
        double life;
        Color color;

        ParticleFx(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.05 + random.nextDouble() * 0.26;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed - 0.06;
            life = 700 + random.nextInt(320);
        }

        boolean step(long dt) {
            x += vx * dt;
            y += vy * dt;
            vy += 0.00018 * dt;
            life -= dt;
            return life > 0;
        }

        void draw(Graphics2D g2) {
            float a = (float) Math.max(0, life / 1000.0);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(x, y, 4, 4));
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    private class Track {
        final double angle = random.nextDouble() * Math.PI * 2;
        final double length = 90 + random.nextDouble() * 190;
        final Color color = random.nextBoolean() ? new Color(120, 240, 255) : new Color(255, 170, 120);

        void draw(Graphics2D g2, int cx, int cy, double progress) {
            double r = length * progress;
            int x2 = cx + (int) (Math.cos(angle) * r);
            int y2 = cy + (int) (Math.sin(angle) * r);

            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(cx, cy, x2, y2);
            g2.fillOval(x2 - 2, y2 - 2, 4, 4);
        }
    }
}
