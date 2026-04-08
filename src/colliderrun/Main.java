package colliderrun;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Qbert Collider Run");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(1100, 760));
            frame.setSize(1280, 860);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new GamePanel());
            frame.setVisible(true);
        });
    }
}
