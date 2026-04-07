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
import java.awt.RadialGradientPaint;
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

    private long lastTickNanos = System.nanoTime();
    private ScreenState screenState = ScreenState.TITLE;

    private final Map<String, Point> cellPositions = new HashMap<>();
    private final List<ParticleFx> particleFx = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();

    private int previousScore;
    private int previousLives;
    private int previousDiscoveryCount;

    private double playerRenderX;
    private double playerRenderY;
    private final List<Point> enemyRender = new ArrayList<>();
    private double pulseT;

    public GamePanel() {
        setBackground(new Color(5, 8, 18));
        setFocusable(true);
        setupKeybinds();

        playerRenderX = 0;
        playerRenderY = 0;
        previousLives = model.lives;

        timer = new Timer(16, this::onFrame);
        timer.start();
    }

    private void setupKeybinds() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> {
                        if (screenState == ScreenState.TITLE || screenState == ScreenState.END) {
                            model.reset();
                            previousScore = 0;
                            previousDiscoveryCount = 0;
                            previousLives = model.lives;
                            screenState = ScreenState.PLAYING;
                        }
                    }
                    case KeyEvent.VK_P -> {
                        if (screenState == ScreenState.PLAYING) {
                            screenState = ScreenState.PAUSED;
                        } else if (screenState == ScreenState.PAUSED) {
                            screenState = ScreenState.PLAYING;
                        }
                    }
                    case KeyEvent.VK_R -> {
                        model.reset();
                        previousScore = 0;
                        previousDiscoveryCount = 0;
                        previousLives = model.lives;
                        particleFx.clear();
                        floatingTexts.clear();
                        screenState = ScreenState.PLAYING;
                    }
                    default -> {
                        if (screenState == ScreenState.PLAYING) {
                            handleMoveKey(e.getKeyCode());
                        }
                    }
                }
                repaint();
            }
        });
    }

    private void handleMoveKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_Q, KeyEvent.VK_NUMPAD7 -> model.movePlayer(-1, -1);
            case KeyEvent.VK_W, KeyEvent.VK_NUMPAD9, KeyEvent.VK_UP -> model.movePlayer(-1, 0);
            case KeyEvent.VK_A, KeyEvent.VK_NUMPAD1, KeyEvent.VK_LEFT -> model.movePlayer(1, 0);
            case KeyEvent.VK_S, KeyEvent.VK_NUMPAD3, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> model.movePlayer(1, 1);
            default -> {
                return;
            }
        }
    }

    private void onFrame(ActionEvent ignored) {
        long now = System.nanoTime();
        long deltaMs = (now - lastTickNanos) / 1_000_000;
        lastTickNanos = now;
        pulseT += deltaMs * 0.0025;

        if (screenState == ScreenState.PLAYING) {
            model.tick(deltaMs);
            postTickEffects();
            if (model.gameOver || model.victory) {
                screenState = ScreenState.END;
            }
        }

        updateAnimations(deltaMs);
        repaint();
    }

    private void postTickEffects() {
        if (model.lastCollisionTriggered) {
            spawnBurst(model.lastCollisionSuccess ? 20 : 12,
                    model.lastCollisionSuccess ? new Color(60, 255, 180) : new Color(255, 90, 90));
            if (model.lastCollisionSuccess) {
                floatingTexts.add(new FloatingText("VALID EVENT", new Color(150, 255, 160)));
            } else {
                floatingTexts.add(new FloatingText("REJECTED", new Color(255, 120, 120)));
            }
        }

        if (model.score > previousScore) {
            floatingTexts.add(new FloatingText("+" + (model.score - previousScore), new Color(255, 220, 110)));
        }
        previousScore = model.score;

        if (model.discoveries.size() > previousDiscoveryCount) {
            floatingTexts.add(new FloatingText("NEW DISCOVERY", new Color(130, 255, 130)));
            spawnBurst(30, new Color(130, 255, 180));
        }
        previousDiscoveryCount = model.discoveries.size();

        if (model.lives < previousLives) {
            spawnBurst(20, new Color(255, 120, 120));
        }
        previousLives = model.lives;
    }

    private void updateAnimations(long deltaMs) {
        Point playerTarget = cellPositions.get(key(model.player.row, model.player.col));
        if (playerTarget != null) {
            playerRenderX = lerp(playerRenderX, playerTarget.x, 0.22);
            playerRenderY = lerp(playerRenderY, playerTarget.y, 0.22);
        }

        while (enemyRender.size() < model.enemies.size()) {
            enemyRender.add(new Point((int) playerRenderX, (int) playerRenderY));
        }

        for (int i = 0; i < model.enemies.size(); i++) {
            Actor enemy = model.enemies.get(i);
            Point target = cellPositions.get(key(enemy.row, enemy.col));
            if (target != null) {
                Point rp = enemyRender.get(i);
                rp.x = (int) lerp(rp.x, target.x, 0.2);
                rp.y = (int) lerp(rp.y, target.y, 0.2);
            }
        }

        particleFx.removeIf(p -> !p.step(deltaMs));
        floatingTexts.removeIf(t -> !t.step(deltaMs));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);
        computeBoardLayout();
        drawBoard(g2);
        drawEffects(g2);
        drawHud(g2);

        if (screenState == ScreenState.TITLE) {
            drawTitleOverlay(g2);
        } else if (screenState == ScreenState.PAUSED) {
            drawPauseOverlay(g2);
        } else if (screenState == ScreenState.END) {
            drawEndOverlay(g2);
        }

        g2.dispose();
    }

    private void drawBackground(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        GradientPaint bg = new GradientPaint(0, 0, new Color(8, 10, 30), 0, h, new Color(4, 4, 10));
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(90, 130, 255, 70));
        for (int i = 0; i < 100; i++) {
            int x = (i * 97) % Math.max(1, w);
            int y = (i * 53 + 37) % Math.max(1, h);
            int r = (i % 3) + 1;
            g2.fillOval(x, y, r, r);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g2.setColor(new Color(70, 130, 255));
        for (int i = 0; i < 6; i++) {
            int y = (int) (120 + i * 90 + Math.sin(pulseT + i) * 12);
            g2.drawLine(0, y, w, y + 30);
        }
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private void computeBoardLayout() {
        cellPositions.clear();
        int centerX = getWidth() / 2;
        int startY = 170;
        int stepX = 95;
        int stepY = 58;

        for (int r = 0; r < GameModel.ROWS; r++) {
            for (int c = 0; c <= r; c++) {
                int x = centerX + (int) ((c - (r * 0.5)) * stepX);
                int y = startY + r * stepY;
                cellPositions.put(key(r, c), new Point(x, y));
            }
        }

        if (playerRenderX == 0 && playerRenderY == 0) {
            Point start = cellPositions.get(key(0, 0));
            playerRenderX = start.x;
            playerRenderY = start.y;
        }
    }

    private void drawBoard(Graphics2D g2) {
        for (int r = GameModel.ROWS - 1; r >= 0; r--) {
            for (int c = 0; c <= r; c++) {
                Point p = cellPositions.get(key(r, c));
                drawIsometricTile(g2, p.x, p.y, model.board[r][c]);
            }
        }

        for (int i = 0; i < model.enemies.size(); i++) {
            Point p = enemyRender.get(i);
            drawEnemy(g2, p.x, p.y, i);
        }

        drawPlayer(g2, (int) playerRenderX, (int) playerRenderY);
    }

    private void drawIsometricTile(Graphics2D g2, int x, int y, BoardTile tile) {
        int hw = 44;
        int hh = 24;
        int depth = 26;

        Color top = tileTop(tile.type, tile.visited);
        Color left = top.darker();
        Color right = top.brighter();

        Polygon topFace = new Polygon(new int[]{x, x + hw, x, x - hw}, new int[]{y - hh, y, y + hh, y}, 4);
        Polygon leftFace = new Polygon(new int[]{x - hw, x, x, x - hw}, new int[]{y, y + hh, y + hh + depth, y + depth}, 4);
        Polygon rightFace = new Polygon(new int[]{x + hw, x, x, x + hw}, new int[]{y, y + hh, y + hh + depth, y + depth}, 4);

        g2.setColor(left);
        g2.fillPolygon(leftFace);
        g2.setColor(right);
        g2.fillPolygon(rightFace);
        g2.setColor(top);
        g2.fillPolygon(topFace);

        g2.setColor(new Color(12, 16, 30));
        g2.setStroke(new BasicStroke(2f));
        g2.drawPolygon(topFace);
        g2.drawPolygon(leftFace);
        g2.drawPolygon(rightFace);

        if (tile.type == TileType.COLLISION) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(pulseT * 2 + tile.row + tile.col));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse * 0.55f));
            g2.setColor(new Color(255, 150, 230));
            g2.fill(new Ellipse2D.Double(x - 12, y - 10, 24, 20));
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    private Color tileTop(TileType type, boolean visited) {
        Color c = switch (type) {
            case ENERGY -> new Color(45, 200, 255);
            case LUMINOSITY -> new Color(255, 190, 60);
            case CALIBRATION -> new Color(120, 240, 150);
            case COLLISION -> new Color(255, 70, 130);
        };
        return visited ? c.brighter() : c;
    }

    private void drawPlayer(Graphics2D g2, int x, int y) {
        int bob = (int) (Math.sin(pulseT * 5) * 4);
        g2.setColor(new Color(250, 145, 45));
        g2.fillOval(x - 20, y - 65 + bob, 40, 34);
        g2.setColor(new Color(255, 220, 180));
        g2.fillOval(x - 12, y - 56 + bob, 24, 18);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 8, y - 51 + bob, 5, 5);
        g2.fillOval(x + 3, y - 51 + bob, 5, 5);
        g2.setColor(new Color(30, 20, 15));
        g2.fillRect(x - 2, y - 42 + bob, 4, 3);
    }

    private void drawEnemy(Graphics2D g2, int x, int y, int index) {
        int bob = (int) (Math.sin(pulseT * 4 + index) * 5);
        Color c = switch (index % 3) {
            case 0 -> new Color(178, 80, 255);
            case 1 -> new Color(90, 210, 255);
            default -> new Color(255, 90, 190);
        };
        g2.setColor(c);
        g2.fillOval(x - 16, y - 55 + bob, 32, 28);
        g2.setColor(new Color(30, 20, 60));
        g2.drawOval(x - 16, y - 55 + bob, 32, 28);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 8, y - 47 + bob, 5, 5);
        g2.fillOval(x + 3, y - 47 + bob, 5, 5);
    }

    private void drawEffects(Graphics2D g2) {
        for (ParticleFx fx : particleFx) {
            fx.draw(g2);
        }
        int y = getHeight() - 74;
        for (int i = 0; i < floatingTexts.size(); i++) {
            floatingTexts.get(i).draw(g2, 26, y - i * 22);
        }
    }

    private void drawHud(Graphics2D g2) {
        int w = getWidth();

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g2.setColor(new Color(14, 24, 52));
        g2.fillRoundRect(18, 18, w - 36, 110, 18, 18);
        g2.setComposite(AlphaComposite.SrcOver);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.drawString("Q*Bert: Collider Run", 30, 48);

        drawMeter(g2, 30, 62, 250, "Beam Energy", model.beamEnergy / 420.0, new Color(40, 210, 255));
        drawMeter(g2, 300, 62, 250, "Luminosity", model.luminosity / 200.0, new Color(255, 200, 70));
        drawMeter(g2, 570, 62, 250, "Calibration", model.calibration / 100.0, new Color(130, 240, 150));

        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.setColor(new Color(255, 230, 160));
        g2.drawString("Score: " + model.score, 30, 118);
        g2.setColor(new Color(255, 145, 145));
        g2.drawString("Lives: " + model.lives, 180, 118);

        g2.setColor(new Color(230, 245, 255));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString("Controls: Q/W/A/S or arrows | P pause | R restart", 300, 118);

        drawDiscoveryBoard(g2);
        drawEventLog(g2);
    }

    private void drawMeter(Graphics2D g2, int x, int y, int w, String label, double value, Color fill) {
        g2.setColor(new Color(90, 110, 150));
        g2.drawString(label + " " + (int) (value * 100) + "%", x, y);

        g2.setColor(new Color(22, 30, 48));
        g2.fillRoundRect(x, y + 6, w, 16, 12, 12);

        int fw = (int) (Math.max(0, Math.min(1, value)) * w);
        g2.setPaint(new GradientPaint(x, y, fill.darker(), x + fw, y + 10, fill));
        g2.fillRoundRect(x, y + 6, fw, 16, 12, 12);
        g2.setColor(new Color(12, 16, 34));
        g2.drawRoundRect(x, y + 6, w, 16, 12, 12);
    }

    private void drawDiscoveryBoard(Graphics2D g2) {
        int x = getWidth() - 300;
        int y = 145;
        g2.setColor(new Color(14, 22, 42, 230));
        g2.fillRoundRect(x, y, 270, 180, 14, 14);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Discovery Board", x + 12, y + 24);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int ty = y + 46;
        for (ParticleType p : ParticleType.values()) {
            boolean found = model.discoveries.contains(p);
            g2.setColor(found ? new Color(130, 255, 140) : new Color(140, 150, 180));
            g2.drawString((found ? "✓ " : "• ") + p.label + "  " + p.thresholdGeV + " GeV", x + 12, ty);
            ty += 20;
        }
    }

    private void drawEventLog(Graphics2D g2) {
        int x = 18;
        int y = getHeight() - 56;
        g2.setColor(new Color(14, 22, 42, 220));
        g2.fillRoundRect(x, y, getWidth() - 36, 38, 12, 12);
        g2.setColor(new Color(220, 240, 255));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString(model.eventLog, x + 10, y + 24);
    }

    private void drawTitleOverlay(Graphics2D g2) {
        drawOverlayBackdrop(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 48));
        g2.drawString("Q*Bert: Collider Run", getWidth() / 2 - 250, getHeight() / 2 - 80);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Tune energy, luminosity, and calibration to discover heavier particles.", getWidth() / 2 - 320, getHeight() / 2 - 34);
        g2.drawString("Press ENTER to start", getWidth() / 2 - 95, getHeight() / 2 + 18);
    }

    private void drawPauseOverlay(Graphics2D g2) {
        drawOverlayBackdrop(g2);
        g2.setColor(new Color(240, 250, 255));
        g2.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2.drawString("Paused", getWidth() / 2 - 80, getHeight() / 2 - 10);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Press P to continue", getWidth() / 2 - 100, getHeight() / 2 + 30);
    }

    private void drawEndOverlay(Graphics2D g2) {
        drawOverlayBackdrop(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 44));
        g2.drawString(model.victory ? "Discovery Complete!" : "Run Failed", getWidth() / 2 - 185, getHeight() / 2 - 32);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g2.drawString("Final Score: " + model.score, getWidth() / 2 - 80, getHeight() / 2 + 8);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2.drawString("Press ENTER or R to start a new run", getWidth() / 2 - 150, getHeight() / 2 + 42);
    }

    private void drawOverlayBackdrop(Graphics2D g2) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2.setColor(new Color(4, 8, 20));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(AlphaComposite.SrcOver);

        float radius = Math.max(getWidth(), getHeight()) * 0.55f;
        RadialGradientPaint glow = new RadialGradientPaint(
                getWidth() / 2f,
                getHeight() / 2f,
                radius,
                new float[]{0f, 1f},
                new Color[]{new Color(90, 120, 255, 120), new Color(0, 0, 0, 0)}
        );
        g2.setPaint(glow);
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private void spawnBurst(int count, Color color) {
        Point p = cellPositions.get(key(model.player.row, model.player.col));
        int px = p != null ? p.x : getWidth() / 2;
        int py = p != null ? p.y - 45 : getHeight() / 2;
        for (int i = 0; i < count; i++) {
            particleFx.add(new ParticleFx(px, py, color));
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
            double speed = 0.06 + random.nextDouble() * 0.28;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed - 0.08;
            life = 420 + random.nextInt(260);
        }

        boolean step(long dt) {
            x += vx * dt;
            y += vy * dt;
            vy += 0.00025 * dt;
            life -= dt;
            return life > 0;
        }

        void draw(Graphics2D g2) {
            float alpha = (float) Math.max(0, life / 700.0);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(x, y, 4, 4));
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    private static class FloatingText {
        String label;
        Color color;
        double life = 1200;

        FloatingText(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        boolean step(long dt) {
            life -= dt;
            return life > 0;
        }

        void draw(Graphics2D g2, int x, int y) {
            float alpha = (float) Math.max(0.15, life / 1200.0);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(color);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString(label, x, y);
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }
}
