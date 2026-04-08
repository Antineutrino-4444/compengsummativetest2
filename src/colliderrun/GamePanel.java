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

    private ScreenState state = ScreenState.TITLE;
    private long lastTickNanos = System.nanoTime();
    private double pulse;

    private double playerRenderX;
    private double playerRenderY;
    private double chamberFlash;

    // phase-2 tunnel mini-game
    private boolean tunnelActive;
    private double tunnelX;
    private double tunnelVelocity;
    private int tunnelStep;
    private int tunnelHits;
    private int tunnelMaxSteps;
    private double gateCenter;
    private double gateWidth;
    private double tunnelTick;

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
                            model.reset();
                            particles.clear();
                            tunnelActive = false;
                            state = ScreenState.PLAYING;
                        }
                    }
                    case KeyEvent.VK_P -> {
                        if (state == ScreenState.PLAYING) state = ScreenState.PAUSED;
                        else if (state == ScreenState.PAUSED) state = ScreenState.PLAYING;
                    }
                    case KeyEvent.VK_R -> {
                        model.reset();
                        particles.clear();
                        tunnelActive = false;
                        state = ScreenState.PLAYING;
                    }
                    case KeyEvent.VK_SPACE -> {
                        if (state == ScreenState.PLAYING && !tunnelActive) {
                            if (model.beginCollisionSequence()) {
                                startTunnelPhase();
                            }
                        }
                    }
                    case KeyEvent.VK_A, KeyEvent.VK_LEFT -> {
                        if (tunnelActive) tunnelVelocity -= 0.12;
                        else if (state == ScreenState.PLAYING) model.movePlayer(1, 0);
                    }
                    case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> {
                        if (tunnelActive) tunnelVelocity += 0.12;
                        else if (state == ScreenState.PLAYING) model.movePlayer(1, 1);
                    }
                    case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> {
                        if (!tunnelActive && state == ScreenState.PLAYING) model.movePlayer(-1, -1);
                    }
                    case KeyEvent.VK_W, KeyEvent.VK_NUMPAD9, KeyEvent.VK_UP -> {
                        if (!tunnelActive && state == ScreenState.PLAYING) model.movePlayer(-1, 0);
                    }
                }
                repaint();
            }
        });
    }

    private void onFrame(ActionEvent ignored) {
        long now = System.nanoTime();
        long dt = (now - lastTickNanos) / 1_000_000;
        lastTickNanos = now;
        pulse += dt * 0.0022;

        if (state == ScreenState.PLAYING) {
            if (tunnelActive) {
                tickTunnel(dt);
            } else {
                model.tick(dt);
            }

            if (model.gameOver || model.victory) {
                state = ScreenState.END;
                tunnelActive = false;
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
        tunnelMaxSteps = 12;
        gateCenter = 0.5;
        gateWidth = 0.22;
        tunnelTick = 0;
    }

    private void tickTunnel(long dt) {
        tunnelTick += dt;
        tunnelX += tunnelVelocity * dt * 0.0016;
        tunnelVelocity *= 0.93;
        tunnelX = Math.max(0.02, Math.min(0.98, tunnelX));

        if (tunnelTick >= 430) {
            tunnelTick = 0;
            tunnelStep++;
            boolean hit = tunnelX >= gateCenter - gateWidth && tunnelX <= gateCenter + gateWidth;
            if (hit) {
                tunnelHits++;
                spawnTunnelSpark(new Color(120, 255, 170));
            } else {
                spawnTunnelSpark(new Color(255, 110, 110));
            }

            gateCenter = 0.18 + random.nextDouble() * 0.64;
            gateWidth = Math.max(0.08, 0.24 - tunnelStep * 0.01);

            if (tunnelStep >= tunnelMaxSteps) {
                finishTunnelPhase();
            }
        }
    }

    private void finishTunnelPhase() {
        tunnelActive = false;
        double quality = tunnelHits / (double) tunnelMaxSteps;
        model.finishCollisionSequence(quality);
        chamberFlash = model.lastCollisionSuccess ? 1.0 : 0.65;
        spawnCollisionParticles(model.lastCollisionSuccess ? 60 : 30);
    }

    private void updateLayout() {
        boardPoints.clear();
        int centerX = getWidth() / 2;
        int topY = 180;
        int gapX = 95;
        int gapY = 62;

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
            playerRenderX = lerp(playerRenderX, p.x, 0.25);
            playerRenderY = lerp(playerRenderY, p.y, 0.25);
        }

        chamberFlash = Math.max(0, chamberFlash - dt / 420.0);
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

        if (tunnelActive) {
            drawTunnelOverlay(g2);
        }

        if (state == ScreenState.TITLE) drawTitle(g2);
        if (state == ScreenState.PAUSED) drawPause(g2);
        if (state == ScreenState.END) drawEnd(g2);

        g2.dispose();
    }

    private void drawBackground(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, new Color(9, 12, 28), 0, h, new Color(5, 6, 14)));
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(90, 120, 180, 40));
        for (int i = 0; i < 120; i++) {
            int x = (i * 73) % Math.max(w, 1);
            int y = (i * 43 + 21) % Math.max(h, 1);
            int r = (i % 3) + 1;
            g2.fillOval(x, y, r, r);
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
        int hw = 42;
        int hh = 23;
        int depth = 24;

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

        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(14, 16, 30));
        g2.drawPolygon(topFace);
        g2.drawPolygon(leftFace);
        g2.drawPolygon(rightFace);

        if (tile.type == TileType.CHAMBER) {
            float a = 0.35f + (float) ((Math.sin(pulse * 3 + tile.row) + 1) * 0.22);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g2.setColor(new Color(255, 180, 220));
            g2.fillOval(x - 14, y - 10, 28, 20);
            g2.setComposite(AlphaComposite.SrcOver);

            if (chamberFlash > 0) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.45 * chamberFlash)));
                g2.setColor(model.lastCollisionSuccess ? new Color(140, 255, 190) : new Color(255, 110, 110));
                g2.fill(new Ellipse2D.Double(x - 70, y - 70, 140, 140));
                g2.setComposite(AlphaComposite.SrcOver);
            }
        }
    }

    private void drawPlayer(Graphics2D g2, int x, int y) {
        int bob = (int) (Math.sin(pulse * 5) * 4);
        g2.setColor(new Color(255, 145, 45));
        g2.fillOval(x - 18, y - 62 + bob, 36, 30);
        g2.setColor(new Color(255, 230, 190));
        g2.fillOval(x - 10, y - 54 + bob, 20, 16);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 7, y - 49 + bob, 4, 4);
        g2.fillOval(x + 3, y - 49 + bob, 4, 4);
    }

    private void drawHud(Graphics2D g2) {
        int w = getWidth();
        g2.setColor(new Color(13, 22, 45, 225));
        g2.fillRoundRect(18, 18, w - 36, 140, 16, 16);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.drawString("Collider Run Console", 30, 48);

        meter(g2, 30, 62, 180, "Beam", model.beamEnergy / 450.0, new Color(80, 205, 255));
        meter(g2, 220, 62, 180, "Focus", model.magnetFocus / 100.0, new Color(170, 150, 255));
        meter(g2, 410, 62, 180, "Lumi", model.luminosity / 100.0, new Color(255, 205, 90));
        meter(g2, 600, 62, 180, "Detector", model.detectorCalibration / 100.0, new Color(130, 240, 165));
        meter(g2, 790, 62, 180, "Heat", model.heat / 120.0, new Color(255, 120, 120));

        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.setColor(new Color(255, 245, 185));
        g2.drawString("Current target: " + model.currentTarget().label + "  (" + model.currentTarget().thresholdGeV + " GeV)", 30, 128);
        g2.drawString("Score: " + model.score + "   Lives: " + model.lives, 480, 128);
        g2.setColor(new Color(200, 225, 255));
        g2.drawString("Goal: tune board -> go to CHAMBER -> SPACE -> clear tunnel -> collision", 720, 128);

        drawDiscoveryList(g2);
        drawEventLog(g2);
    }

    private void drawTunnelOverlay(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
        g2.setColor(new Color(5, 10, 24));
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.SrcOver);

        int tunnelW = 520;
        int tunnelH = 280;
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
        int gateRadius = (int) ((tunnelW - 80) * gateWidth * 0.25);
        g2.setColor(new Color(130, 255, 170));
        g2.drawOval(gateX - gateRadius, laneY - 40, gateRadius * 2, 80);

        int beamX = tx + 40 + (int) ((tunnelW - 80) * tunnelX);
        g2.setColor(new Color(255, 210, 120));
        g2.fillOval(beamX - 10, laneY - 10, 20, 20);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString("Tunnel Phase", tx + 20, ty + 34);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.drawString("Steer beam packet through gates using A/D or Left/Right", tx + 20, ty + 58);
        g2.drawString("Hits: " + tunnelHits + " / " + tunnelMaxSteps + "   Step: " + tunnelStep + " / " + tunnelMaxSteps, tx + 20, ty + 82);
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
        int x = getWidth() - 300;
        int y = 172;
        g2.setColor(new Color(14, 22, 42, 220));
        g2.fillRoundRect(x, y, 270, 180, 14, 14);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Confirmed events", x + 12, y + 24);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int ty = y + 46;
        for (ParticleType p : ParticleType.values()) {
            boolean found = model.discoveries.contains(p);
            g2.setColor(found ? new Color(130, 255, 150) : new Color(130, 140, 170));
            g2.drawString((found ? "OK " : "-- ") + p.label + "  " + p.thresholdGeV + " GeV", x + 12, ty);
            ty += 19;
        }
    }

    private void drawEventLog(Graphics2D g2) {
        int x = 18;
        int y = getHeight() - 56;
        g2.setColor(new Color(12, 20, 40, 220));
        g2.fillRoundRect(x, y, getWidth() - 36, 38, 10, 10);
        g2.setColor(new Color(225, 240, 255));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString(model.eventLog, x + 10, y + 24);
    }

    private void drawTitle(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 46));
        g2.drawString("Qbert Collider Run", getWidth() / 2 - 220, getHeight() / 2 - 70);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Phase 1: tune collider modules on board", getWidth() / 2 - 185, getHeight() / 2 - 20);
        g2.drawString("Phase 2: chamber tunnel mini-game decides collision quality", getWidth() / 2 - 255, getHeight() / 2 + 10);
        g2.drawString("Win by confirming every target particle in order.", getWidth() / 2 - 185, getHeight() / 2 + 40);
        g2.drawString("Press ENTER to start.", getWidth() / 2 - 95, getHeight() / 2 + 78);
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
        for (int i = 0; i < 9; i++) {
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
            double speed = 0.08 + random.nextDouble() * 0.35;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed - 0.1;
            life = 540 + random.nextInt(320);
        }

        boolean step(long dt) {
            x += vx * dt;
            y += vy * dt;
            vy += 0.00024 * dt;
            life -= dt;
            return life > 0;
        }

        void draw(Graphics2D g2) {
            float a = (float) Math.max(0, life / 900.0);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(x, y, 4, 4));
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }
}
