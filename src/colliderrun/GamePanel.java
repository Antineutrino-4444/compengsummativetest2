package colliderrun;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class GamePanel extends JPanel {
    private final GameModel model = new GameModel();
    private final Timer timer;
    private long lastTickNanos = System.nanoTime();

    public GamePanel() {
        setBackground(new Color(8, 10, 22));
        setFocusable(true);
        setupKeybinds();

        timer = new Timer(16, this::onFrame);
        timer.start();
    }

    private void setupKeybinds() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_Q -> model.movePlayer(-1, -1); // up-left
                    case KeyEvent.VK_W -> model.movePlayer(-1, 0);  // up-right
                    case KeyEvent.VK_A -> model.movePlayer(1, 0);   // down-left
                    case KeyEvent.VK_S -> model.movePlayer(1, 1);   // down-right
                    case KeyEvent.VK_R -> model.reset();
                    default -> {
                        return;
                    }
                }
                repaint();
            }
        });
    }

    private void onFrame(ActionEvent ignored) {
        long now = System.nanoTime();
        long deltaMs = (now - lastTickNanos) / 1_000_000;
        lastTickNanos = now;
        model.tick(deltaMs);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int centerX = w / 2;
        int startY = 130;
        int stepX = 50;
        int stepY = 36;

        Map<String, int[]> positions = new HashMap<>();
        for (int r = 0; r < GameModel.ROWS; r++) {
            for (int c = 0; c <= r; c++) {
                int x = centerX + (c - r / 2) * stepX;
                int y = startY + r * stepY;
                positions.put(key(r, c), new int[]{x, y});
                drawTile(g2, x, y, model.board[r][c]);
            }
        }

        for (Actor enemy : model.enemies) {
            int[] p = positions.get(key(enemy.row, enemy.col));
            drawEnemy(g2, p[0], p[1]);
        }

        int[] playerPos = positions.get(key(model.player.row, model.player.col));
        drawPlayer(g2, playerPos[0], playerPos[1]);
        drawHud(g2, w, h);
        g2.dispose();
    }

    private void drawTile(Graphics2D g2, int x, int y, BoardTile tile) {
        Polygon diamond = new Polygon(
                new int[]{x, x + 32, x, x - 32},
                new int[]{y - 18, y, y + 18, y},
                4
        );
        Color base = switch (tile.type) {
            case ENERGY -> new Color(0, 180, 255);
            case LUMINOSITY -> new Color(255, 190, 30);
            case CALIBRATION -> new Color(140, 235, 130);
            case COLLISION -> new Color(255, 90, 130);
        };
        if (tile.visited) {
            base = base.brighter();
        }
        g2.setColor(base);
        g2.fillPolygon(diamond);
        g2.setColor(new Color(20, 20, 35));
        g2.setStroke(new BasicStroke(2));
        g2.drawPolygon(diamond);
    }

    private void drawPlayer(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(255, 130, 40));
        g2.fillOval(x - 12, y - 36, 24, 24);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 6, y - 30, 4, 4);
        g2.fillOval(x + 2, y - 30, 4, 4);
    }

    private void drawEnemy(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(180, 70, 255));
        g2.fillOval(x - 11, y - 32, 22, 22);
        g2.setColor(new Color(35, 0, 60));
        g2.drawOval(x - 11, y - 32, 22, 22);
    }

    private void drawHud(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(18, 26, 50, 220));
        g2.fillRoundRect(18, 16, w - 36, 92, 14, 14);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.drawString("Q*Bert: Collider Run", 30, 44);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString("Energy: " + fmt(model.beamEnergy) + " GeV", 30, 66);
        g2.drawString("Luminosity: " + fmt(model.luminosity), 220, 66);
        g2.drawString("Calibration: " + fmt(model.calibration) + "%", 390, 66);
        g2.drawString("Lives: " + model.lives + "    Score: " + model.score, 30, 88);

        g2.drawString("Discovered: " + model.discoveries.size() + "/" + ParticleType.values().length, 220, 88);
        g2.drawString("Controls: Q/W/A/S to hop, R to restart", 430, 88);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.setColor(new Color(230, 245, 255));
        g2.drawString(model.eventLog, 24, h - 20);

        int dx = w - 250;
        int dy = 120;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (ParticleType p : ParticleType.values()) {
            boolean found = model.discoveries.contains(p);
            g2.setColor(found ? new Color(100, 245, 120) : new Color(140, 140, 155));
            g2.drawString((found ? "✓ " : "• ") + p.label + "  (" + p.thresholdGeV + " GeV)", dx, dy);
            dy += 18;
        }

        if (model.gameOver || model.victory) {
            g2.setColor(new Color(5, 8, 20, 200));
            g2.fillRect(0, 0, w, h);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 40));
            g2.drawString(model.victory ? "Discovery Complete!" : "Run Failed", w / 2 - 180, h / 2 - 20);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g2.drawString("Press R to start a new collider run", w / 2 - 160, h / 2 + 20);
        }
    }

    private static String key(int r, int c) {
        return r + "," + c;
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }
}
