import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class laserpointereEditor extends JFrame {

    static class LineSegment {
        double x1, y1, x2, y2;
        int id;

        LineSegment(double x1, double y1, double x2, double y2, int id) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.id = id;
        }

        void flip() {
            double tempX = x1;
            double tempY = y1;
            x1 = x2;
            y1 = y2;
            x2 = tempX;
            y2 = tempY;
        }
    }

    private final List<LineSegment> segments = new ArrayList<>();
    private double panX = 0;
    private double panY = 0;
    private double zoom = 25.0; // pixels per world unit
    
    private Double drawStartX = null;
    private Double drawStartY = null;
    private double currentMouseWorldX = 0;
    private double currentMouseWorldY = 0;
    
    private final double SNAP_SIZE = 0.5; // Grid snap interval
    
    private int nextId = 0;
    private File currentFile;
    private JLabel statusLabel;
    private CanvasPanel canvasPanel;

    public laserpointereEditor() {
        setTitle("laserpointereEditor - Map Editor");
        setSize(850, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        canvasPanel = new CanvasPanel();
        add(canvasPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel(" Click to draw. Arrow keys to pan. Select/Flip to fix normals.", JLabel.LEFT);
        bottomPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton openBtn = new JButton("Open Map JSON");
        JButton saveBtn = new JButton("Save Map JSON");
        JButton flipBtn = new JButton("Flip Last Wall");
        JButton clearBtn = new JButton("Clear");

        openBtn.addActionListener(e -> openMap());
        saveBtn.addActionListener(e -> saveMap());
        flipBtn.addActionListener(e -> {
            if (!segments.isEmpty()) {
                segments.get(segments.size() - 1).flip();
                canvasPanel.repaint();
                statusLabel.setText(" Flipped last wall direction (reversed normal).");
            }
        });
        clearBtn.addActionListener(e -> {
            segments.clear();
            nextId = 0;
            canvasPanel.repaint();
        });

        btnPanel.add(openBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(flipBtn);
        btnPanel.add(clearBtn);
        bottomPanel.add(btnPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private double snap(double val) {
        return Math.round(val / SNAP_SIZE) * SNAP_SIZE;
    }

    class CanvasPanel extends JPanel {
        private int lastMouseX, lastMouseY;

        public CanvasPanel() {
            setBackground(new Color(30, 30, 30));
            setFocusable(true);

            InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = getActionMap();

            im.put(KeyStroke.getKeyStroke("UP"), "panUp");
            im.put(KeyStroke.getKeyStroke("DOWN"), "panDown");
            im.put(KeyStroke.getKeyStroke("LEFT"), "panLeft");
            im.put(KeyStroke.getKeyStroke("RIGHT"), "panRight");

            double panStep = 1.0;

            am.put("panUp", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) { panY += panStep; repaint(); }
            });
            am.put("panDown", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) { panY -= panStep; repaint(); }
            });
            am.put("panLeft", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) { panX += panStep; repaint(); }
            });
            am.put("panRight", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) { panX -= panStep; repaint(); }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();

                    double wx = snap((e.getX() - getWidth() / 2.0) / zoom - panX);
                    double wy = snap((e.getY() - getHeight() / 2.0) / zoom - panY);

                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (drawStartX == null) {
                            drawStartX = wx;
                            drawStartY = wy;
                        } else {
                            segments.add(new LineSegment(drawStartX, drawStartY, wx, wy, nextId++));
                            drawStartX = null;
                            drawStartY = null;
                        }
                        repaint();
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        drawStartX = null;
                        drawStartY = null;
                        repaint();
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateWorldMouse(e.getX(), e.getY());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();

                    if (SwingUtilities.isRightMouseButton(e) || e.isShiftDown()) {
                        panX += dx / zoom;
                        panY += dy / zoom;
                    }
                    updateWorldMouse(e.getX(), e.getY());
                    repaint();
                }
            });

            addMouseWheelListener(e -> {
                if (e.getWheelRotation() < 0) zoom *= 1.15;
                else zoom /= 1.15;
                zoom = Math.max(2.0, Math.min(200.0, zoom));
                repaint();
            });
        }

        private void updateWorldMouse(int sx, int sy) {
            currentMouseWorldX = snap((sx - getWidth() / 2.0) / zoom - panX);
            currentMouseWorldY = snap((sy - getHeight() / 2.0) / zoom - panY);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g2d) {
            super.paintComponent(g2d);
            Graphics2D g = (Graphics2D) g2d;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            double gridStep = SNAP_SIZE;
            while (gridStep * zoom < 15) {
                gridStep *= 2.0;
            }

            
            g.setColor(new Color(45, 45, 45));
            double startWorldX = -panX - w / 2.0 / zoom;
            double endWorldX = -panX + w / 2.0 / zoom;
            double startWorldY = -panY - h / 2.0 / zoom;
            double endWorldY = -panY + h / 2.0 / zoom;

            for (double gx = Math.floor(startWorldX / gridStep) * gridStep; gx <= endWorldX; gx += gridStep) {
                int sx = (int) ((gx + panX) * zoom + w / 2.0);
                g.drawLine(sx, 0, sx, h);
            }
            for (double gy = Math.floor(startWorldY / gridStep) * gridStep; gy <= endWorldY; gy += gridStep) {
                int sy = (int) ((gy + panY) * zoom + h / 2.0);
                g.drawLine(0, sy, w, sy);
            }

            
            g.setColor(new Color(80, 80, 80));
            int ox = (int) (panX * zoom + w / 2.0);
            int oy = (int) (panY * zoom + h / 2.0);
            g.drawLine(ox, 0, ox, h);
            g.drawLine(0, oy, w, oy);

            
            g.setStroke(new BasicStroke(2.5f));
            for (LineSegment seg : segments) {
                g.setColor(new Color(220, 160, 60));
                int x1 = (int) ((seg.x1 + panX) * zoom + w / 2.0);
                int y1 = (int) ((seg.y1 + panY) * zoom + h / 2.0);
                int x2 = (int) ((seg.x2 + panX) * zoom + w / 2.0);
                int y2 = (int) ((seg.y2 + panY) * zoom + h / 2.0);
                g.drawLine(x1, y1, x2, y2);
                
                
                g.setColor(new Color(100, 200, 255));
                g.fillOval(x2 - 2, y2 - 2, 4, 4);

                // ID text
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g.drawString("id:" + seg.id, (x1 + x2) / 2 + 4, (y1 + y2) / 2 - 4);
            }

            
            if (drawStartX != null && drawStartY != null) {
                g.setColor(new Color(100, 220, 100));
                int x1 = (int) ((drawStartX + panX) * zoom + w / 2.0);
                int y1 = (int) ((drawStartY + panY) * zoom + h / 2.0);
                int x2 = (int) ((currentMouseWorldX + panX) * zoom + w / 2.0);
                int y2 = (int) ((currentMouseWorldY + panY) * zoom + h / 2.0);
                g.drawLine(x1, y1, x2, y2);
                g.fillOval(x1 - 3, y1 - 3, 6, 6);
            }

            // HUD
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g.drawString(String.format("Cursor: (%.2f, %.2f) | Snap: %.1f | Walls: %d", currentMouseWorldX, currentMouseWorldY, SNAP_SIZE, segments.size()), 10, 20);
        }
    }

    private void openMap() {
        FileDialog fd = new FileDialog(this, "Open Map JSON", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".json"));
        fd.setVisible(true);
        
        String filename = fd.getFile();
        String directory = fd.getDirectory();
        
        if (filename != null && directory != null) {
            currentFile = new File(directory, filename);
            try {
                segments.clear();
                String content = new String(Files.readAllBytes(currentFile.toPath()));
                String[] items = content.split("\\{");
                int maxId = -1;
                for (String item : items) {
                    if (item.contains("x1")) {
                        double x1 = extractJsonValue(item, "x1");
                        double y1 = extractJsonValue(item, "y1");
                        double x2 = extractJsonValue(item, "x2");
                        double y2 = extractJsonValue(item, "y2");
                        int id = (int) extractJsonValue(item, "id");
                        segments.add(new LineSegment(x1, y1, x2, y2, id));
                        maxId = Math.max(maxId, id);
                    }
                }
                nextId = maxId + 1;
                statusLabel.setText(" Loaded " + segments.size() + " walls from " + currentFile.getName());
                canvasPanel.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error reading map file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveMap() {
        if (currentFile == null) {
            FileDialog fd = new FileDialog(this, "Save Map JSON", FileDialog.SAVE);
            fd.setFile("map.json");
            fd.setVisible(true);
            if (fd.getFile() != null && fd.getDirectory() != null) {
                String path = fd.getDirectory() + fd.getFile();
                if (!path.endsWith(".json")) path += ".json";
                currentFile = new File(path);
            } else {
                return;
            }
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < segments.size(); i++) {
                LineSegment s = segments.get(i);
                sb.append(String.format("  {\n    \"x1\": %.2f,\n    \"y1\": %.2f,\n    \"x2\": %.2f,\n    \"y2\": %.2f,\n    \"id\": %d\n  }%s\n",
                        s.x1, s.y1, s.x2, s.y2, s.id, (i < segments.size() - 1) ? "," : ""));
            }
            sb.append("]\n");

            Files.write(currentFile.toPath(), sb.toString().getBytes());
            statusLabel.setText(" Saved map to " + currentFile.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save map file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    double extractJsonValue(String jsonSnippet, String key) {
        try {
            int keyIdx = jsonSnippet.indexOf("\"" + key + "\"");
            if (keyIdx == -1) return 0;
            int colonIdx = jsonSnippet.indexOf(":", keyIdx);
            int commaIdx = jsonSnippet.indexOf(",", colonIdx);
            int braceIdx = jsonSnippet.indexOf("}", colonIdx);
            int endIdx = (commaIdx != -1 && (braceIdx == -1 || commaIdx < braceIdx)) ? commaIdx : braceIdx;
            String valStr = jsonSnippet.substring(colonIdx + 1, endIdx).trim();
            return Double.parseDouble(valStr);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new laserpointereEditor().setVisible(true));
    }
}