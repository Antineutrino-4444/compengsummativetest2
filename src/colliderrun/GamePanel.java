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
    private final Map<String, Point> points = new HashMap<>();

    private long lastNanos = System.nanoTime();
    private double pulse;
    private double playerX;
    private double playerY;

    private int mouseX;
    private int mouseY;
    private String hover;

    public GamePanel() {
        setBackground(new Color(8, 10, 20));
        setFocusable(true);
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
                            model.startRun();
                        } else if (model.phase == GameModel.Phase.CLASSIFICATION) {
                            model.submitLabel();
                        }
                    }
                    case KeyEvent.VK_Q -> model.moveCalibrationPlayer(-1, -1);
                    case KeyEvent.VK_E -> model.moveCalibrationPlayer(-1, 0);
                    case KeyEvent.VK_A -> model.moveCalibrationPlayer(1, 0);
                    case KeyEvent.VK_D -> model.moveCalibrationPlayer(1, 1);
                    case KeyEvent.VK_LEFT -> model.selectPrevLabel();
                    case KeyEvent.VK_RIGHT -> model.selectNextLabel();
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
        layoutBoard();

        Point p = points.get(key(model.player.row, model.player.col));
        if (p != null) {
            if (playerX == 0) {
                playerX = p.x;
                playerY = p.y;
            }
            playerX += (p.x - playerX) * 0.24;
            playerY += (p.y - playerY) * 0.24;
        }

        repaint();
    }

    private void layoutBoard() {
        points.clear();
        int cx = getWidth() / 2;
        int top = 190;
        int gapY = 78;
        int gapX = 95;
        for (int r = 0; r < model.rows; r++) {
            for (int c = 0; c <= r; c++) {
                int x = cx + (int) ((c - r * 0.5) * gapX);
                int y = top + r * gapY;
                points.put(key(r, c), new Point(x, y));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);
        if (model.phase == GameModel.Phase.CALIBRATION) drawCalibrationStage(g2);
        else if (model.phase == GameModel.Phase.CLASSIFICATION) drawClassificationStage(g2);
        else if (model.phase == GameModel.Phase.RESULTS) drawResults(g2);
        else drawTitle(g2);

        drawHud(g2);
        g2.dispose();
    }

    private void drawBackground(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, new Color(10, 14, 34), 0, getHeight(), new Color(6, 8, 18)));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawCalibrationStage(Graphics2D g2) {
        for (int r = model.rows - 1; r >= 0; r--) {
            for (int c = 0; c <= r; c++) {
                Point p = points.get(key(r, c));
                drawPlatform(g2, p.x, p.y, model.charged[r][c]);
            }
        }

        for (Actor e : model.enemies) {
            Point p = points.get(key(e.row, e.col));
            g2.setColor(new Color(255, 90, 130));
            g2.fillOval(p.x - 14, p.y - 56, 28, 20);
        }

        int bob = (int) (Math.sin(pulse * 5) * 4);
        g2.setColor(new Color(255, 165, 70));
        g2.fillOval((int) playerX - 16, (int) playerY - 58 + bob, 32, 24);
    }

    private void drawPlatform(Graphics2D g2, int x, int y, boolean charged) {
        int hw = 40, hh = 20, depth = 18;
        Color top = charged ? new Color(120, 255, 170) : new Color(80, 140, 255);

        Polygon topFace = new Polygon(new int[]{x, x + hw, x, x - hw}, new int[]{y - hh, y, y + hh, y}, 4);
        Polygon left = new Polygon(new int[]{x - hw, x, x, x - hw}, new int[]{y, y + hh, y + hh + depth, y + depth}, 4);
        Polygon right = new Polygon(new int[]{x + hw, x, x, x + hw}, new int[]{y, y + hh, y + hh + depth, y + depth}, 4);

        g2.setColor(top.darker());
        g2.fillPolygon(left);
        g2.setColor(top.brighter());
        g2.fillPolygon(right);
        g2.setColor(top);
        g2.fillPolygon(topFace);
        g2.setColor(new Color(20, 22, 35));
        g2.drawPolygon(topFace);
        g2.drawPolygon(left);
        g2.drawPolygon(right);
    }

    private void drawClassificationStage(Graphics2D g2) {
        int cx = getWidth() / 2;
        int cy = getHeight() / 2 + 40;

        g2.setColor(new Color(120, 160, 220));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - 70, cy - 70, 140, 140);
        g2.drawOval(cx - 130, cy - 130, 260, 260);
        g2.drawOval(cx - 190, cy - 190, 380, 380);

        hover = null;
        double progress = 1.0 - (model.eventMsLeft / 6500.0);
        progress = Math.max(0.1, Math.min(1.0, progress));

        int idx = 1;
        for (GameModel.EventTrack t : model.currentTracks) {
            int x2 = cx + (int) (Math.cos(t.angle) * t.length * progress);
            int y2 = cy + (int) (Math.sin(t.angle) * t.length * progress);

            Color color = switch (t.flavor) {
                case MUON_PAIR -> new Color(130, 255, 240);
                case JET_BURST -> new Color(255, 170, 100);
                case MISSING_ENERGY -> new Color(255, 120, 120);
                case BOSON_CANDIDATE -> new Color(180, 255, 140);
            };
            g2.setColor(color);
            g2.drawLine(cx, cy, x2, y2);
            g2.fillOval(x2 - 3, y2 - 3, 6, 6);

            if (Math.hypot(mouseX - x2, mouseY - y2) < 10) {
                hover = "Track " + idx + " | q=" + (t.charge > 0 ? "+1" : "-1") + " | class bias=" + t.flavor;
            }
            idx++;
        }

        drawClassifier(g2);
        if (hover != null) drawHover(g2, hover, mouseX, mouseY);
    }

    private void drawClassifier(Graphics2D g2) {
        String[] labels = {"MUON_PAIR", "JET_BURST", "MISSING_ENERGY", "BOSON_CANDIDATE"};
        int x = 40, y = 220;
        g2.setColor(new Color(20, 30, 55, 220));
        g2.fillRoundRect(x, y, 300, 160, 12, 12);
        g2.setColor(Color.WHITE);
        g2.drawString("Classify current event", x + 12, y + 24);

        for (int i = 0; i < labels.length; i++) {
            g2.setColor(i == model.selectedLabelIndex ? new Color(255, 220, 120) : new Color(180, 190, 210));
            g2.drawString((i == model.selectedLabelIndex ? "> " : "  ") + labels[i], x + 14, y + 48 + i * 24);
        }
        g2.setColor(new Color(200, 220, 255));
        g2.drawString("LEFT/RIGHT to choose, ENTER to submit", x + 12, y + 144);
    }

    private void drawHover(Graphics2D g2, String text, int x, int y) {
        int w = g2.getFontMetrics().stringWidth(text) + 14;
        int h = 22;
        int bx = Math.min(getWidth() - w - 10, x + 12);
        int by = Math.max(10, y - 24);
        g2.setColor(new Color(12, 22, 44, 230));
        g2.fillRoundRect(bx, by, w, h, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawRoundRect(bx, by, w, h, 8, 8);
        g2.drawString(text, bx + 7, by + 15);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(15, 22, 48, 230));
        g2.fillRoundRect(16, 16, getWidth() - 32, 130, 14, 14);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2.drawString("Collider Shift", 28, 42);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString("Score: " + model.score + "    Lives: " + model.lives, 28, 66);
        g2.drawString("Status: " + model.status, 28, 88);

        if (model.phase == GameModel.Phase.CALIBRATION) {
            g2.drawString("Charged: " + model.chargedCount + "/" + model.chargedTarget + "    Time: " + (model.calibrationMsLeft / 1000) + "s", 28, 110);
        } else if (model.phase == GameModel.Phase.CLASSIFICATION) {
            g2.drawString("Detector power T/C/M: " + model.trackerPower + "/" + model.caloPower + "/" + model.muonPower, 28, 110);
            g2.drawString("Classification: " + model.correctTags + "/" + model.totalTags + "    Event timer: " + (model.eventMsLeft / 1000.0), 28, 128);
        }
    }

    private void drawTitle(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2.drawString("Qbert Collider Run - Overhaul", getWidth() / 2 - 280, getHeight() / 2 - 60);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("Stage 1: QEAD platform charge + avoid drones", getWidth() / 2 - 210, getHeight() / 2 - 10);
        g2.drawString("Stage 2: classify detector events with realistic-looking track patterns", getWidth() / 2 - 300, getHeight() / 2 + 18);
        g2.drawString("Press ENTER to start", getWidth() / 2 - 95, getHeight() / 2 + 62);
    }

    private void drawResults(Graphics2D g2) {
        overlay(g2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 40));
        g2.drawString("Shift Complete", getWidth() / 2 - 140, getHeight() / 2 - 30);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g2.drawString("Final score: " + model.score, getWidth() / 2 - 88, getHeight() / 2 + 10);
        g2.drawString("Correct classifications: " + model.correctTags + "/" + Math.max(1, model.totalTags), getWidth() / 2 - 150, getHeight() / 2 + 40);
        g2.drawString("Press ENTER to play again", getWidth() / 2 - 125, getHeight() / 2 + 80);
    }

    private void overlay(Graphics2D g2) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2.setColor(new Color(5, 10, 22));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private static String key(int r, int c) {
        return r + "," + c;
    }
}
