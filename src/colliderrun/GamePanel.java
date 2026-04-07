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
                        state = ScreenState.PLAYING;
                    }
                    case KeyEvent.VK_SPACE -> {
                        if (state == ScreenState.PLAYING) {
                            model.triggerCollision();
                            if (model.lastCollisionTriggered) {
                                chamberFlash = model.lastCollisionSuccess ? 1.0 : 0.65;
                                spawnCollisionParticles(model.lastCollisionSuccess ? 55 : 28);
                            }
                        }
                    }
                    default -> {
                        if (state == ScreenState.PLAYING) {
                            handleMove(e.getKeyCode());
                        }
                    }
                }
                repaint();
            }
        });
    }

    private void handleMove(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> model.movePlayer(-1, -1);
            case KeyEvent.VK_W, KeyEvent.VK_NUMPAD9, KeyEvent.VK_UP -> model.movePlayer(-1, 0);
            case KeyEvent.VK_A, KeyEvent.VK_NUMPAD1, KeyEvent.VK_LEFT -> model.movePlayer(1, 0);
            case KeyEvent.VK_S, KeyEvent.VK_NUMPAD3, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> model.movePlayer(1, 1);
        }
    }

    private void onFrame(ActionEvent ignored) {
        long now = System.nanoTime();
        long dt = (now - lastTickNanos) / 1_000_000;
        lastTickNanos = now;
        pulse += dt * 0.0022;

        if (state == ScreenState.PLAYING) {
            model.tick(dt);
            if (model.gameOver || model.victory) {
                state = ScreenState.END;
            }
        }

        updateLayout();
        animate(dt);
        repaint();
    }

    private void updateLayout() {
        boardPoints.clear();
        int centerX = getWidth() / 2;
        int topY = 170;
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
        drawBeamline(g2);
        drawBoard(g2);
        drawParticles(g2);
        drawHud(g2);

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

    private void drawBeamline(Graphics2D g2) {
        Point chamber = boardPoints.get(key(GameModel.ROWS - 1, GameModel.ROWS / 2));
        if (chamber == null) return;

        int sourceLeftX = getWidth() / 2 - 360;
        int sourceRightX = getWidth() / 2 + 360;
        int sourceY = chamber.y - 250;

        float alpha = 0.24f + (float) (Math.sin(pulse * 2.0) * 0.08 + 0.08);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setStroke(new BasicStroke(6f));
        g2.setColor(new Color(70, 220, 255));
        g2.drawLine(sourceLeftX, sourceY, chamber.x - 12, chamber.y - 6);
        g2.setColor(new Color(255, 120, 180));
        g2.drawLine(sourceRightX, sourceY, chamber.x + 12, chamber.y - 6);
        g2.setComposite(AlphaComposite.SrcOver);

        if (chamberFlash > 0) {
            float flash = (float) Math.min(1.0, chamberFlash);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flash * 0.65f));
            g2.setColor(model.lastCollisionSuccess ? new Color(140, 255, 190) : new Color(255, 110, 110));
            g2.fill(new Ellipse2D.Double(chamber.x - 70, chamber.y - 70, 140, 140));
            g2.setComposite(AlphaComposite.SrcOver);
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
            g2.fillOval(x - 12, y - 10, 24, 20);
            g2.setComposite(AlphaComposite.SrcOver);
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

    private void drawParticles(Graphics2D g2) {
        for (ParticleFx fx : particles) {
            fx.draw(g2);
        }
    }

    private void drawHud(Graphics2D g2) {
        int w = getWidth();
        g2.setColor(new Color(13, 22, 45, 225));
        g2.fillRoundRect(18, 18, w - 36, 138, 16, 16);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.drawString("Collider Run: Operations Console", 30, 48);

        meter(g2, 30, 62, 220, "Beam", model.beamEnergy / 450.0, new Color(80, 205, 255));
        meter(g2, 265, 62, 220, "Focus", model.magnetFocus / 100.0, new Color(170, 150, 255));
        meter(g2, 500, 62, 220, "Lumi", model.luminosity / 100.0, new Color(255, 205, 90));
        meter(g2, 735, 62, 220, "Detector", model.detectorCalibration / 100.0, new Color(130, 240, 165));
        meter(g2, 970, 62, 220, "Heat", model.heat / 120.0, new Color(255, 120, 120));

        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.setColor(new Color(255, 245, 185));
        g2.drawString("Target: " + model.currentTarget().label + "  (" + model.currentTarget().thresholdGeV + " GeV)", 30, 128);
        g2.drawString("Score: " + model.score + "    Lives: " + model.lives, 380, 128);
        g2.setColor(new Color(200, 225, 255));
        g2.drawString("Move: Q/W/A/S or arrows   Fire collision: SPACE   Pause: P", 640, 128);

        drawDiscoveryList(g2);
        drawEventLog(g2);
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
        int x = getWidth() - 290;
        int y = 170;
        g2.setColor(new Color(14, 22, 42, 220));
        g2.fillRoundRect(x, y, 260, 180, 14, 14);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Validated Signatures", x + 12, y + 24);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int ty = y + 46;
        for (ParticleType p : ParticleType.values()) {
            boolean found = model.discoveries.contains(p);
            g2.setColor(found ? new Color(130, 255, 150) : new Color(130, 140, 170));
            g2.drawString((found ? "✓ " : "• ") + p.label + "  " + p.thresholdGeV + " GeV", x + 12, ty);
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
        g2.drawString("Q*Bert: Collider Run", getWidth() / 2 - 245, getHeight() / 2 - 70);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 21));
        g2.drawString("This is a collider operations game: tune modules, then fire collisions from the chamber.", getWidth() / 2 - 430, getHeight() / 2 - 20);
        g2.drawString("Hit target signatures in order from μ+μ- to t t̄.", getWidth() / 2 - 205, getHeight() / 2 + 12);
        g2.drawString("Press ENTER to start.", getWidth() / 2 - 110, getHeight() / 2 + 50);
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

    private void spawnCollisionParticles(int count) {
        Point chamber = boardPoints.get(key(GameModel.ROWS - 1, GameModel.ROWS / 2));
        if (chamber == null) return;
        Color color = model.lastCollisionSuccess ? new Color(140, 255, 190) : new Color(255, 120, 120);
        for (int i = 0; i < count; i++) {
            particles.add(new ParticleFx(chamber.x, chamber.y, color));
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
