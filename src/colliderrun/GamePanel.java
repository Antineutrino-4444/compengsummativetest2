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
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.Map;

public class GamePanel extends JPanel {
    private final GameModel model = new GameModel();
    private final Timer timer;
    private final Map<String, Point> tuningPoints = new HashMap<>();

    private long lastNanos = System.nanoTime();
    private double pulse;
    private double tunerX;
    private double tunerY;

    private int mouseX;
    private int mouseY;
    private String hover;

    public GamePanel() {
        setFocusable(true);
        setBackground(new Color(7, 9, 20));
        setupInput();
        setupMouse();
        timer = new Timer(16, this::frame);
        timer.start();
    }

    private void setupInput() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> {
                        if (model.phase == GameModel.Phase.TITLE || model.phase == GameModel.Phase.RESULTS) {
                            model.startNewGame();
                        } else if (model.phase == GameModel.Phase.RUN) {
                            model.trigger();
                        }
                    }
                    case KeyEvent.VK_Q -> model.moveTuner(-1, -1);
                    case KeyEvent.VK_E -> model.moveTuner(-1, 0);
                    case KeyEvent.VK_A -> {
                        if (model.phase == GameModel.Phase.TUNING) model.moveTuner(1, 0);
                        else model.moveDetector(-0.04);
                    }
                    case KeyEvent.VK_D -> {
                        if (model.phase == GameModel.Phase.TUNING) model.moveTuner(1, 1);
                        else model.moveDetector(0.04);
                    }
                    case KeyEvent.VK_1 -> model.upgradeTracker();
                    case KeyEvent.VK_2 -> model.upgradeCalo();
                    case KeyEvent.VK_3 -> model.upgradeMuon();
                    case KeyEvent.VK_SPACE -> model.trigger();
                }
            }
        });
    }

    private void setupMouse() {
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
        });
    }

    private void frame(ActionEvent ignored) {
        long now = System.nanoTime();
        long dt = (now - lastNanos) / 1_000_000;
        lastNanos = now;
        pulse += dt * 0.002;
        model.tick(dt);
        layoutTuning();

        Point p = tuningPoints.get(key(model.tuner.row, model.tuner.col));
        if (p != null) {
            if (tunerX == 0) {
                tunerX = p.x;
                tunerY = p.y;
            }
            tunerX += (p.x - tunerX) * 0.22;
            tunerY += (p.y - tunerY) * 0.22;
        }

        repaint();
    }

    private void layoutTuning() {
        tuningPoints.clear();
        int cx = getWidth() / 2;
        int top = 190;
        int gapY = 82;
        int gapX = 102;
        for (int r = 0; r < model.tuningRows; r++) {
            for (int c = 0; c <= r; c++) {
                int x = cx + (int) ((c - r * 0.5) * gapX);
                int y = top + r * gapY;
                tuningPoints.put(key(r, c), new Point(x, y));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);
        if (model.phase == GameModel.Phase.TUNING) drawTuning(g2);
        else if (model.phase == GameModel.Phase.RUN) drawRun(g2);
        else if (model.phase == GameModel.Phase.RESULTS) drawResults(g2);
        else drawTitle(g2);

        drawHud(g2);
        g2.dispose();
    }

    private void drawBackground(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, new Color(9, 12, 30), 0, getHeight(), new Color(4, 6, 14)));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawTuning(Graphics2D g2) {
        for (int r = model.tuningRows - 1; r >= 0; r--) {
            for (int c = 0; c <= r; c++) {
                Point p = tuningPoints.get(key(r, c));
                drawNode(g2, p.x, p.y, model.tuned[r][c]);
            }
        }

        for (Actor e : model.enemies) {
            Point p = tuningPoints.get(key(e.row, e.col));
            g2.setColor(new Color(255, 90, 130));
            g2.fillOval(p.x - 14, p.y - 56, 28, 20);
        }

        int bob = (int) (Math.sin(pulse * 5) * 4);
        g2.setColor(new Color(255, 160, 60));
        g2.fillOval((int) tunerX - 16, (int) tunerY - 58 + bob, 32, 26);
    }

    private void drawNode(Graphics2D g2, int x, int y, boolean tuned) {
        int hw = 42, hh = 20, d = 18;
        Color c = tuned ? new Color(130, 255, 180) : new Color(90, 150, 255);
        Polygon top = new Polygon(new int[]{x, x + hw, x, x - hw}, new int[]{y - hh, y, y + hh, y}, 4);
        Polygon left = new Polygon(new int[]{x - hw, x, x, x - hw}, new int[]{y, y + hh, y + hh + d, y + d}, 4);
        Polygon right = new Polygon(new int[]{x + hw, x, x, x + hw}, new int[]{y, y + hh, y + hh + d, y + d}, 4);

        g2.setColor(c.darker());
        g2.fillPolygon(left);
        g2.setColor(c.brighter());
        g2.fillPolygon(right);
        g2.setColor(c);
        g2.fillPolygon(top);
        g2.setColor(new Color(20, 20, 30));
        g2.drawPolygon(top);
        g2.drawPolygon(left);
        g2.drawPolygon(right);
    }

    private void drawRun(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        int detectorY = h - 120;

        // detector layers
        int cx = w / 2;
        int cy = h / 2 + 30;
        g2.setColor(new Color(100, 140, 220));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - 100, cy - 100, 200, 200);
        g2.drawOval(cx - 170, cy - 170, 340, 340);
        g2.drawOval(cx - 240, cy - 240, 480, 480);

        hover = null;

        for (GameModel.FallingEvent e : model.events) {
            int x = (int) (e.x * w);
            int y = (int) (e.y * h);
            Color color = e.signalLike ? new Color(120, 255, 180) : new Color(255, 130, 130);
            g2.setColor(color);
            g2.fillOval(x - 8, y - 8, 16, 16);

            if (e.signalLike) {
                g2.setColor(new Color(120, 255, 220, 100));
                g2.drawLine(cx, cy, x, y);
            }

            if (Math.hypot(mouseX - x, mouseY - y) < 10) {
                hover = "Event E=" + String.format("%.1f", e.baseEnergy) + " GeV | " + (e.signalLike ? "signal-like" : "background");
            }
        }

        // trigger window and detector paddle
        int paddleX = (int) (model.detectorX * w);
        g2.setColor(new Color(255, 210, 120));
        g2.fillRoundRect(paddleX - 50, detectorY, 100, 16, 8, 8);
        g2.setColor(new Color(180, 220, 255));
        g2.drawRoundRect(paddleX - 80, detectorY - 48, 160, 60, 10, 10);

        if (hover != null) drawHover(g2, hover, mouseX, mouseY);

        drawDiscoveryBoard(g2);
    }

    private void drawDiscoveryBoard(Graphics2D g2) {
        int x = getWidth() - 300;
        int y = 170;
        g2.setColor(new Color(18, 24, 42, 220));
        g2.fillRoundRect(x, y, 280, 190, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Produced Particles", x + 12, y + 24);

        int yy = y + 48;
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        for (ParticleType p : ParticleType.values()) {
            boolean found = model.discovered.contains(p);
            g2.setColor(found ? new Color(130, 255, 150) : new Color(140, 150, 170));
            g2.drawString((found ? "OK " : "-- ") + p.label + "  " + p.thresholdGeV + " GeV", x + 12, yy);
            yy += 20;
        }
    }

    private void drawHover(Graphics2D g2, String text, int x, int y) {
        int w = g2.getFontMetrics().stringWidth(text) + 14;
        int h = 22;
        int bx = Math.min(getWidth() - w - 8, x + 12);
        int by = Math.max(8, y - 26);
        g2.setColor(new Color(10, 20, 40, 230));
        g2.fillRoundRect(bx, by, w, h, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawRoundRect(bx, by, w, h, 8, 8);
        g2.drawString(text, bx + 7, by + 15);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(16, 22, 45, 230));
        g2.fillRoundRect(14, 14, getWidth() - 28, 140, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 21));
        g2.drawString("Collider Run", 26, 40);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString("Score: " + model.score + "  Lives: " + model.lives + "  Resources: " + model.resources, 26, 64);
        g2.drawString("Beam: " + fmt(model.beamEnergy) + "  Lumi: " + fmt(model.luminosity) + "  Align: " + fmt(model.magnetAlignment), 26, 86);
        g2.drawString("Detector Lv (1/2/3 keys): Tracker " + model.trackerLevel + "  Calo " + model.caloLevel + "  Muon " + model.muonLevel, 26, 108);
        g2.drawString(model.status, 26, 130);

        if (model.phase == GameModel.Phase.TUNING) {
            g2.drawString("Tuning: " + model.tunedCount + "/" + model.tunedTarget + "  Time: " + (model.tuningMsLeft / 1000) + "s", 760, 64);
        }
        if (model.phase == GameModel.Phase.RUN) {
            g2.drawString("Run time left: " + (model.runMsLeft / 1000) + "s  Last Eeff: " + fmt(model.lastEffectiveEnergy), 760, 64);
            g2.drawString("Controls: A/D move trigger, SPACE/ENTER trigger event", 760, 86);
        }
    }

    private void drawTitle(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2.drawString("Qbert Collider Run - Steam-Style Rework", getWidth() / 2 - 360, getHeight() / 2 - 60);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Minor stage: QEAD platform tuning. Main stage: real-time collision operations.", getWidth() / 2 - 320, getHeight() / 2 - 10);
        g2.drawString("Build detector systems, trigger collisions, and produce heavier particles.", getWidth() / 2 - 300, getHeight() / 2 + 18);
        g2.drawString("Press ENTER to start", getWidth() / 2 - 95, getHeight() / 2 + 62);
    }

    private void drawResults(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2.drawString("Run Complete", getWidth() / 2 - 140, getHeight() / 2 - 36);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g2.drawString("Score: " + model.score, getWidth() / 2 - 70, getHeight() / 2 + 4);
        g2.drawString("Particles discovered: " + model.discovered.size() + "/" + ParticleType.values().length, getWidth() / 2 - 145, getHeight() / 2 + 36);
        g2.drawString("Press ENTER to play again", getWidth() / 2 - 125, getHeight() / 2 + 76);
    }

    private void overlay(Graphics2D g2) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f));
        g2.setColor(new Color(5, 10, 22));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private static String key(int r, int c) {
        return r + "," + c;
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
