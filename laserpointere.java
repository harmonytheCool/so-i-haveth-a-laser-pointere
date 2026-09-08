import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class laserpointere extends Canvas
        implements Runnable,
        KeyListener,
        MouseListener,
        MouseMotionListener {

    // ============================================================
    // WINDOW
    // ============================================================

    static final int W = 800;
    static final int H = 600;

    // ============================================================
    // CAMERA
    // ============================================================

    static final float PITCH_MIN = -89.0f;
    static final float PITCH_MAX = 89.0f;

    float yaw = 0.0f;
    float pitch = 0.0f;

    float fov = 75.0f;

    static final float FOV_MIN = 60.0f;
    static final float FOV_MAX = 120.0f;

    // ============================================================
    // MOUSE
    // ============================================================

    float sensitivity = 0.12f;

    static final float SENS_MIN = 0.01f;
    static final float SENS_MAX = 2.00f;

    boolean invertMouse = false;

    boolean mouseCaptured = false;

    int mouseX = W / 2;
    int mouseY = H / 2;

    int lastMouseX = W / 2;
    int lastMouseY = H / 2;

    boolean haveMousePosition = false;

    Robot robot;
    boolean suppressNextMotion = false;

    int rawDX = 0;
    int rawDY = 0;

    float mouseSpeed = 0.0f;

    long lastMouseNanos = 0L;

    float accumulatedDX = 0.0f;
    float accumulatedDY = 0.0f;

    // ============================================================
    // PLAYER
    // ============================================================

    float x = 2.0f;
    float y = 2.0f;
    float z = 0.0f;

    float verticalVelocity = 0.0f;

    boolean onGround = true;

    float forwardSpeed = 9.5f;
    float sideSpeed = 6.5f;
    float jumpSpeed = 10.0f;
    float gravity = 15.0f;

    static final float SPEED_MIN = 2.0f;
    static final float SPEED_MAX = 30.0f;
    static final float JUMP_MIN = 2.0f;
    static final float JUMP_MAX = 25.0f;

    static final float PLAYER_RADIUS = 0.25f;
    static final float EYE_HEIGHT = 1.55f;

    // ============================================================
    // INPUT
    // ============================================================

    boolean[] keys = new boolean[512];

    boolean menuOpen = false;
    int menuSelection = 0; // 0: Sensitivity, 1: FOV, 2: Move Speed, 3: Jump Height, 4: Invert, 5: Load Map, 6: Done

    boolean consoleOpen = false;

    // ============================================================
    // RENDERING
    // ============================================================

    JFrame window;
    BufferStrategy buffer;
    BufferedImage frame;

    Thread gameThread;

    boolean running = true;

    // ============================================================
    // CONSOLE
    // ============================================================

    String consoleInput = "";

    final List<String> console =
            new ArrayList<>();

    // ============================================================
    // WORLD
    // ============================================================

    final List<Wall> walls =
            new ArrayList<>();

    // ============================================================
    // BSP
    // ============================================================

    BSPNode bspRoot;

    int bspNodeCount = 0;
    int bspSplitCount = 0;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public laserpointere() {

        frame =
                new BufferedImage(
                        W,
                        H,
                        BufferedImage.TYPE_INT_RGB
                );

        setPreferredSize(
                new Dimension(
                        W,
                        H
                )
        );

        setFocusable(true);

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        buildMap();

        buildBSP();

        console.add(
                "LASERPOINTERE ENGINE v0.7"
        );

        console.add(
                "BSP tree: "
                + bspNodeCount
                + " nodes, "
                + bspSplitCount
                + " splits"
        );

        console.add(
                "JDK-only Java FPS prototype"
        );

        console.add(
                "Mouse: relative event deltas"
        );

        console.add(
                "NO Robot cursor warping"
        );

        console.add(
                "Opaque 3D world-space floor"
        );

        console.add(
                "ESC = configuration"
        );

        console.add(
                "~ = console"
        );
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(
            String[] args
    ) throws Exception {

        if (
                args.length > 0
                &&
                args[0].equals("--bsptest")
        ) {

            bspSelfTest();

            return;
        }

        if (
                args.length > 0
                &&
                args[0].equals("--hallwaytest")
        ) {

            hallwaySelfTest();

            return;
        }

        new laserpointere().start();
    }

    static void hallwaySelfTest() {

        laserpointere lp =
                new laserpointere();

        lp.enterHallwayMode();

        System.out.println(
                "after enter: chunks "
                + lp.hallwayMinChunk
                + ".."
                + lp.hallwayMaxChunk
                + "  walls=" + lp.walls.size()
                + "  bspNodes=" + lp.bspNodeCount
        );

        int maxWallsSeen = 0;

        for (
                int step = 0;
                step < 400;
                step++
        ) {

            lp.x += 0.2f;

            lp.ensureHallwayChunks();

            maxWallsSeen =
                    Math.max(maxWallsSeen, lp.walls.size());
        }

        System.out.println(
                "after 80 units walked: x=" + lp.x
                + "  chunks " + lp.hallwayMinChunk + ".." + lp.hallwayMaxChunk
                + "  walls=" + lp.walls.size()
                + "  maxWallsSeenDuringWalk=" + maxWallsSeen
                + "  bspNodes=" + lp.bspNodeCount
        );

        List<Wall> a = new ArrayList<>();
        List<Wall> b = new ArrayList<>();

        laserpointere probe1 = new laserpointere();
        probe1.generateHallwayChunk(42);
        a.addAll(probe1.walls);

        laserpointere probe2 = new laserpointere();
        probe2.generateHallwayChunk(42);
        b.addAll(probe2.walls);

        boolean same = a.size() == b.size();

        if (same) {
            for (int i = 0; i < a.size(); i++) {
                Wall wa = a.get(i);
                Wall wb = b.get(i);
                if (wa.x1 != wb.x1 || wa.y1 != wb.y1 || wa.x2 != wb.x2 || wa.y2 != wb.y2) {
                    same = false;
                    break;
                }
            }
        }

        System.out.println(
                "chunk 42 regenerated identically: " + same
                + " (" + a.size() + " walls each)"
        );

        boolean corridorClear =
                !lp.blocked(
                        lp.x,
                        (float)(HALLWAY_WIDTH * 0.5)
                );

        System.out.println(
                "player position blocked after walk: " + !corridorClear
                + " (expect false)"
        );
    }

    static void bspSelfTest() {

        laserpointere lp =
                new laserpointere();

        System.out.println(
                "nodes=" + lp.bspNodeCount
                + " splits=" + lp.bspSplitCount
                + " walls=" + lp.walls.size()
        );

        double[][] cams = {
                {2, 2}, {10, 1}, {6, 8}, {9.5, 1}, {11.5, 1}
        };

        double origLen = 0;

        for (Wall w : lp.walls) {
            origLen += Math.hypot(w.x2 - w.x1, w.y2 - w.y1);
        }

        System.out.println("original total wall length=" + origLen);

        lp.x = 0;
        lp.y = 0;
        lp.yaw = 0;

        Wall fullyBehind = new Wall(-5, -1, -3, 1, 999);
        Wall straddling = new Wall(-1, -1, 1, 1, 998);
        Wall fullyAhead = new Wall(3, -1, 3, 1, 997);

        System.out.println("fully-behind wall projects to: "
                + lp.projectWall(fullyBehind) + " (expect null)");
        System.out.println("straddling-near-plane wall projects to: "
                + lp.projectWall(straddling) + " (expect NOT null)");
        System.out.println("fully-ahead wall projects to: "
                + lp.projectWall(fullyAhead) + " (expect NOT null)");

        for (double[] cam : cams) {

            List<Wall> out = new ArrayList<>();

            lp.traverseBSP(lp.bspRoot, cam[0], cam[1], out);

            double fragLen = 0;

            for (Wall w : out) {
                fragLen += Math.hypot(w.x2 - w.x1, w.y2 - w.y1);
            }

            System.out.println(
                    "cam=(" + cam[0] + "," + cam[1] + ") -> "
                    + out.size() + " fragments, totalLength=" + fragLen
                    + " matches=" + (Math.abs(fragLen - origLen) < 1e-6)
            );
        }
    }

    // ============================================================
    // START
    // ============================================================

    void start() {

        window =
                new JFrame(
                        "laserpointere.java"
                );

        window.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        window.setResizable(false);
        window.setAlwaysOnTop(false);

        window.add(this);
        window.setSize(W, H);
        window.setLocationRelativeTo(null);

        window.setVisible(true);
        window.toFront();

        requestFocusInWindow();

        SwingUtilities.invokeLater(
                () -> {
                    requestFocusInWindow();
                    captureMouse();
                }
        );

        gameThread =
                new Thread(
                        this,
                        "laserpointere-main"
                );

        gameThread.start();
    }

    // ============================================================
    // GAME LOOP
    // ============================================================

    @Override
    public void run() {

        while (
                !isDisplayable()
        ) {
            Thread.yield();
        }

        createBufferStrategy(2);

        buffer =
                getBufferStrategy();

        long last =
                System.nanoTime();

        while (
                running
        ) {

            long now =
                    System.nanoTime();

            float dt =
                    (
                            now - last
                    )
                    /
                    1_000_000_000.0f;

            last =
                    now;

            if (
                    dt > 0.05f
            ) {
                dt =
                        0.05f;
            }

            UserCmd cmd =
                    CL_CreateMove();

            movePlayer(
                    cmd,
                    dt
            );

            ensureHallwayChunks();

            render();

            Graphics g =
                    buffer.getDrawGraphics();

            try {

                g.drawImage(
                        frame,
                        0,
                        0,
                        getWidth(),
                        getHeight(),
                        null
                );

            } finally {

                g.dispose();
            }

            buffer.show();

            Thread.yield();
        }
    }

    // ============================================================
    // GOLD SRC-STYLE CREATE MOVE
    // ============================================================

    UserCmd CL_CreateMove() {

        UserCmd cmd =
                new UserCmd();

        if (
                menuOpen
                ||
                consoleOpen
        ) {

            accumulatedDX =
                    0.0f;

            accumulatedDY =
                    0.0f;

            return cmd;
        }

        float dx =
                accumulatedDX;

        float dy =
                accumulatedDY;

        accumulatedDX =
                0.0f;

        accumulatedDY =
                0.0f;

        yaw +=
                dx * sensitivity;

        if (
                invertMouse
        ) {

            pitch +=
                    dy * sensitivity;

        } else {

            pitch -=
                    dy * sensitivity;
        }

        pitch =
                clamp(
                        pitch,
                        PITCH_MIN,
                        PITCH_MAX
                );

        while (
                yaw >= 360.0f
        ) {

            yaw -=
                    360.0f;
        }

        while (
                yaw < 0.0f
        ) {

            yaw +=
                    360.0f;
        }

        if (
                down(
                        KeyEvent.VK_W
                )
        ) {

            cmd.forward +=
                    forwardSpeed;
        }

        if (
                down(
                        KeyEvent.VK_S
                )
        ) {

            cmd.forward -=
                    forwardSpeed;
        }

        if (
                down(
                        KeyEvent.VK_D
                )
        ) {

            cmd.side +=
                    sideSpeed;
        }

        if (
                down(
                        KeyEvent.VK_A
                )
        ) {

            cmd.side -=
                    sideSpeed;
        }

        if (
                down(
                        KeyEvent.VK_SPACE
                )
        ) {

            cmd.up =
                    jumpSpeed;
        }

        return cmd;
    }

    // ============================================================
    // PLAYER MOVEMENT
    // ============================================================

    void movePlayer(
            UserCmd cmd,
            float dt
    ) {

        double radians =
                Math.toRadians(
                        yaw
                );

        float forwardX =
                (float)Math.cos(
                        radians
                );

        float forwardY =
                (float)Math.sin(
                        radians
                );

        float rightX =
                -forwardY;

        float rightY =
                forwardX;

        float moveX =
                forwardX * cmd.forward
                +
                rightX * cmd.side;

        float moveY =
                forwardY * cmd.forward
                +
                rightY * cmd.side;

        float length =
                (float)Math.hypot(
                        moveX,
                        moveY
                );

        if (
                length > forwardSpeed
        ) {

            float scale =
                    forwardSpeed
                    /
                    length;

            moveX *=
                    scale;

            moveY *=
                    scale;
        }

        tryMove(
                moveX * dt,
                moveY * dt
        );

        if (
                onGround
                &&
                cmd.up > 0.0f
        ) {

            verticalVelocity =
                    jumpSpeed;

            onGround =
                    false;
        }

        verticalVelocity -=
                gravity * dt;

        z +=
                verticalVelocity * dt;

        if (
                z <= 0.0f
        ) {

            z =
                    0.0f;

            verticalVelocity =
                    0.0f;

            onGround =
                    true;
        }
    }

    // ============================================================
    // COLLISION
    // ============================================================

    void tryMove(
            float dx,
            float dy
    ) {

        if (
                !blocked(
                        x + dx,
                        y
                )
        ) {

            x +=
                    dx;
        }

        if (
                !blocked(
                        x,
                        y + dy
                )
        ) {

            y +=
                    dy;
        }
    }

    boolean blocked(
            float px,
            float py
    ) {

        for (
                Wall wall :
                walls
        ) {

            if (
                    distanceToSegment(
                            px,
                            py,
                            wall.x1,
                            wall.y1,
                            wall.x2,
                            wall.y2
                    )
                    <
                    PLAYER_RADIUS
            ) {

                return true;
            }
        }

        return false;
    }

    double distanceToSegment(
            double px,
            double py,
            double x1,
            double y1,
            double x2,
            double y2
    ) {

        double dx =
                x2 - x1;

        double dy =
                y2 - y1;

        double lengthSquared =
                dx * dx
                +
                dy * dy;

        if (
                lengthSquared == 0.0
        ) {

            return Math.hypot(
                    px - x1,
                    py - y1
            );
        }

        double t =
                (
                        (px - x1) * dx
                        +
                        (py - y1) * dy
                )
                /
                lengthSquared;

        t =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                t
                        )
                );

        double closestX =
                x1 + t * dx;

        double closestY =
                y1 + t * dy;

        return Math.hypot(
                px - closestX,
                py - closestY
        );
    }

    // ============================================================
    // RENDER
    // ============================================================

    void render() {

        Graphics2D g =
                frame.createGraphics();

        try {

            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF
            );

            g.setColor(
                    new Color(
                            105,
                            145,
                            175
                    )
            );

            g.fillRect(
                    0,
                    0,
                    W,
                    H
            );

            renderFloor(
                    g
            );

            renderWalls(
                    g
            );

            renderHUD(
                    g
            );

            if (
                    menuOpen
            ) {

                renderMenu(
                        g
                );
            }

            if (
                    consoleOpen
            ) {

                renderConsole(
                        g
                );
            }

        } finally {

            g.dispose();
        }
    }

    // ============================================================
    // 3D FLOOR
    // ============================================================

    void renderFloor(
            Graphics2D g
    ) {

        double size = 64.0;

        Point3 p1 =
                project(
                        x - size,
                        y - size,
                        0.0
                );

        Point3 p2 =
                project(
                        x + size,
                        y - size,
                        0.0
                );

        Point3 p3 =
                project(
                        x + size,
                        y + size,
                        0.0
                );

        Point3 p4 =
                project(
                        x - size,
                        y + size,
                        0.0
                );

        g.setColor(
                new Color(
                        48,
                        49,
                        45
                )
        );

        g.fillRect(
                0,
                H / 2,
                W,
                H / 2
        );

        final int radius = 40;

        double originX =
                Math.floor(x);

        double originY =
                Math.floor(y);

        for (
                int i = -radius;
                i <= radius;
                i++
        ) {

            double gx =
                    originX + i;

            drawFloorLine(
                    g,
                    gx,
                    originY - radius,
                    gx,
                    originY + radius,
                    false
            );
        }

        for (
                int i = -radius;
                i <= radius;
                i++
        ) {

            double gy =
                    originY + i;

            drawFloorLine(
                    g,
                    originX - radius,
                    gy,
                    originX + radius,
                    gy,
                    false
            );
        }

        drawFloorLine(
                g,
                originX,
                originY - radius,
                originX,
                originY + radius,
                true
        );

        drawFloorLine(
                g,
                originX - radius,
                originY,
                originX + radius,
                originY,
                true
        );
    }

    void drawFloorLine(
            Graphics2D g,
            double x1,
            double y1,
            double x2,
            double y2,
            boolean axis
    ) {

        double[] clipped =
                clipSegmentToNearPlane(
                        x1,
                        y1,
                        x2,
                        y2
                );

        if (
                clipped == null
        ) {

            return;
        }

        Point3 a =
                project(
                        clipped[0],
                        clipped[1],
                        0.0
                );

        Point3 b =
                project(
                        clipped[2],
                        clipped[3],
                        0.0
                );

        if (
                a == null
                ||
                b == null
        ) {

            return;
        }

        if (
                axis
        ) {

            g.setColor(
                    new Color(
                            105,
                            105,
                            98
                    )
            );

        } else {

            double depth =
                    (
                            a.depth
                            +
                            b.depth
                    )
                    /
                    2.0;

            int brightness =
                    (int)Math.max(
                            35,
                            Math.min(
                                    105,
                                    105
                                    -
                                    depth * 2.2
                            )
                    );

            g.setColor(
                    new Color(
                            brightness,
                            brightness,
                            Math.max(
                                    30,
                                    brightness - 4
                            )
                    )
            );
        }

        g.drawLine(
                a.x,
                a.y,
                b.x,
                b.y
        );
    }

    // ============================================================
    // NEAR-PLANE CLIPPING
    // ============================================================

    static final double NEAR_CLIP = 0.15;

    double cameraForward(
            double worldX,
            double worldY
    ) {

        double dx =
                worldX - x;

        double dy =
                worldY - y;

        double radians =
                Math.toRadians(
                        yaw
                );

        return dx * Math.cos(radians)
                + dy * Math.sin(radians);
    }

    double[] clipSegmentToNearPlane(
            double x1,
            double y1,
            double x2,
            double y2
    ) {

        double f1 =
                cameraForward(
                        x1,
                        y1
                );

        double f2 =
                cameraForward(
                        x2,
                        y2
                );

        if (
                f1 < NEAR_CLIP
                &&
                f2 < NEAR_CLIP
        ) {

            return null;
        }

        if (
                f1 < NEAR_CLIP
        ) {

            double t =
                    (NEAR_CLIP - f1)
                    /
                    (f2 - f1);

            x1 =
                    x1 + t * (x2 - x1);

            y1 =
                    y1 + t * (y2 - y1);

        } else if (
                f2 < NEAR_CLIP
        ) {

            double t =
                    (NEAR_CLIP - f2)
                    /
                    (f1 - f2);

            x2 =
                    x2 + t * (x1 - x2);

            y2 =
                    y2 + t * (y1 - y2);
        }

        return new double[] {
                x1, y1, x2, y2
        };
    }

    // ============================================================
    // PROJECTION
    // ============================================================

    Point3 project(
            double worldX,
            double worldY,
            double height
    ) {

        double dx =
                worldX - x;

        double dy =
                worldY - y;

        double radians =
                Math.toRadians(
                        yaw
                );

        double forward =
                dx * Math.cos(radians)
                +
                dy * Math.sin(radians);

        double side =
                -dx * Math.sin(radians)
                +
                dy * Math.cos(radians);

        if (
                forward <= 0.05
        ) {

            return null;
        }

        double focalLength =
                (
                        W / 2.0
                )
                /
                Math.tan(
                        Math.toRadians(
                                fov
                        )
                        /
                        2.0
                );

        double pitchRadians =
                Math.toRadians(
                        clamp(
                                pitch,
                                PITCH_MIN,
                                PITCH_MAX
                        )
                );

        double pitchOffset =
                Math.tan(
                        pitchRadians
                )
                *
                focalLength;

        if (
                pitchOffset > H * 8.0
        ) {

            pitchOffset =
                    H * 8.0;
        }

        if (
                pitchOffset < -H * 8.0
        ) {

            pitchOffset =
                    -H * 8.0;
        }

        double screenCenter =
                H / 2.0
                +
                pitchOffset;

        int screenX =
                (int)(
                        W / 2.0
                        +
                        side
                        /
                        forward
                        *
                        focalLength
                );

        int screenY =
                (int)(
                        screenCenter
                        -
                        (
                                height
                                -
                                (EYE_HEIGHT + z)
                        )
                        /
                        forward
                        *
                        focalLength
                );

        return new Point3(
                screenX,
                screenY,
                forward
        );
    }

    // ============================================================
    // WALL RENDERING
    // ============================================================

    void renderWalls(
            Graphics2D g
    ) {

        List<Wall> drawOrder =
                new ArrayList<>();

        traverseBSP(
                bspRoot,
                x,
                y,
                drawOrder
        );

        List<Projection> projections =
                new ArrayList<>();

        for (
                Wall wall :
                drawOrder
        ) {

            Projection p =
                    projectWall(
                            wall
                    );

            if (
                    p != null
            ) {

                projections.add(
                        p
                );
            }
        }

        for (
                Projection p :
                projections
        ) {

            int brightness =
                    (int)Math.max(
                            30,
                            Math.min(
                                    190,
                                    185
                                    -
                                    p.depth * 13
                            )
                    );

            int variation =
                    (
                            p.id * 23
                    )
                    %
                    35;

            int r =
                    Math.max(
                            25,
                            brightness
                            -
                            variation
                    );

            int gr =
                    Math.max(
                            25,
                            brightness
                            -
                            variation
                            +
                            8
                    );

            int b =
                    Math.max(
                            25,
                            brightness
                            -
                            variation
                            +
                            5
                    );

            g.setColor(
                    new Color(
                            r,
                            gr,
                            b
                    )
            );

            Polygon polygon =
                    new Polygon();

            polygon.addPoint(
                    p.bl.x,
                    p.bl.y
            );

            polygon.addPoint(
                    p.br.x,
                    p.br.y
            );

            polygon.addPoint(
                    p.tr.x,
                    p.tr.y
            );

            polygon.addPoint(
                    p.tl.x,
                    p.tl.y
            );

            g.fillPolygon(
                    polygon
            );

            g.setColor(
                    new Color(
                            25,
                            25,
                            25
                    )
            );

            g.drawPolygon(
                    polygon
            );
        }
    }

    Projection projectWall(
            Wall wall
    ) {

        double[] clipped =
                clipSegmentToNearPlane(
                        wall.x1,
                        wall.y1,
                        wall.x2,
                        wall.y2
                );

        if (
                clipped == null
        ) {

            return null;
        }

        double wx1 = clipped[0];
        double wy1 = clipped[1];
        double wx2 = clipped[2];
        double wy2 = clipped[3];

        Point3 bl =
                project(
                        wx1,
                        wy1,
                        0
                );

        Point3 br =
                project(
                        wx2,
                        wy2,
                        0
                );

        Point3 tl =
                project(
                        wx1,
                        wy1,
                        3
                );

        Point3 tr =
                project(
                        wx2,
                        wy2,
                        3
                );

        if (
                bl == null
                ||
                br == null
                ||
                tl == null
                ||
                tr == null
        ) {

            return null;
        }

        return new Projection(
                bl,
                br,
                tr,
                tl,
                (
                        bl.depth
                        +
                        br.depth
                )
                /
                2.0,
                wall.id
        );
    }

    // ============================================================
    // HUD
    // ============================================================

    void renderHUD(
            Graphics2D g
    ) {

        g.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        12
                )
        );

        g.setColor(
                Color.WHITE
        );

        g.drawString(
                String.format(
                        "LASERPOINTERE  POS %.2f %.2f %.2f",
                        x,
                        y,
                        z
                ),
                12,
                20
        );

        g.drawString(
                String.format(
                        "YAW %.1f  PITCH %.1f  FOV %.0f",
                        yaw,
                        pitch,
                        fov
                ),
                12,
                36
        );

        g.drawString(
                String.format(
                        "MOUSE X %d Y %d  DX %+d DY %+d",
                        mouseX,
                        mouseY,
                        rawDX,
                        rawDY
                ),
                12,
                52
        );

        g.drawString(
                String.format(
                        "MOUSE SPEED %.0f px/s  SENS %.2f",
                        mouseSpeed,
                        sensitivity
                ),
                12,
                68
        );

        g.drawString(
                String.format(
                        "CAPTURED %s  INVERT %s",
                        mouseCaptured
                                ? "YES"
                                : "NO",
                        invertMouse
                                ? "YES"
                                : "NO"
                ),
                12,
                84
        );

        g.drawString(
                String.format(
                        "BSP NODES %d  SPLITS %d",
                        bspNodeCount,
                        bspSplitCount
                ),
                12,
                100
        );

        int centerX =
                W / 2;

        int centerY =
                H / 2;

        g.drawLine(
                centerX - 6,
                centerY,
                centerX + 6,
                centerY
        );

        g.drawLine(
                centerX,
                centerY - 6,
                centerX,
                centerY + 6
        );

        g.drawString(
                "WASD MOVE  SPACE JUMP  ESC CONFIG  ~ CONSOLE",
                12,
                H - 14
        );
    }

    // ============================================================
    // CONFIG MENU (DEBUG HUD STYLE)
    // ============================================================

    void renderMenu(
            Graphics2D g
    ) {
        // Dark translucent overlay
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(0, 0, W, H);

        g.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        12
                )
        );

        // Menu items
        String[] labels = {
            "SENSITIVITY",
            "FIELD OF VIEW",
            "MOVE SPEED",
            "JUMP HEIGHT",
            "INVERT MOUSE",
            "LOAD MAP JSON...",
            "DONE"
        };

        int startY = 20;
        int spacing = 18;

        for (int i = 0; i < labels.length; i++) {
            int y = startY + (i * spacing);
            boolean selected = (i == menuSelection);

            String line;
            if (i == 0) {
                line = String.format("%s %s %.2f", selected ? ">" : " ", labels[i], sensitivity);
            } else if (i == 1) {
                line = String.format("%s %s %.0f", selected ? ">" : " ", labels[i], fov);
            } else if (i == 2) {
                line = String.format("%s %s %.1f", selected ? ">" : " ", labels[i], forwardSpeed);
            } else if (i == 3) {
                line = String.format("%s %s %.1f", selected ? ">" : " ", labels[i], jumpSpeed);
            } else if (i == 4) {
                line = String.format("%s %s %s", selected ? ">" : " ", labels[i], invertMouse ? "YES" : "NO");
            } else {
                line = String.format("%s %s", selected ? ">" : " ", labels[i]);
            }

            g.setColor(Color.WHITE);
            g.drawString(line, 12, y);
        }

        g.drawString(
                "UP/DOWN NAVIGATE  LEFT/RIGHT ADJUST  ENTER SELECT  ESC CLOSE",
                12,
                H - 14
        );
    }

    // ============================================================
    // CONSOLE
    // ============================================================

    void renderConsole(
            Graphics2D g
    ) {

        g.setColor(
                new Color(
                        0,
                        0,
                        0,
                        220
                )
        );

        g.fillRect(
                0,
                0,
                W,
                245
        );

        g.setColor(
                Color.WHITE
        );

        g.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        13
                )
        );

        int textY =
                20;

        int start =
                Math.max(
                        0,
                        console.size() - 13
                );

        for (
                int i = start;
                i < console.size();
                i++
        ) {

            g.drawString(
                    console.get(i),
                    12,
                    textY
            );

            textY +=
                    16;
        }

        g.setColor(
                new Color(
                        190,
                        190,
                        190
                )
        );

        g.drawString(
                "] "
                        +
                        consoleInput
                        +
                        "_",
                12,
                232
        );
    }

    // ============================================================
    // CONSOLE COMMANDS
    // ============================================================

    void executeConsoleCommand() {

        String command =
                consoleInput.trim();

        if (
                command.isEmpty()
        ) {

            return;
        }

        console.add(
                "] " + command
        );

        String lower =
                command.toLowerCase();

        try {

            if (
                    lower.equals(
                            "help"
                    )
            ) {

                console.add(
                        "sensitivity <number>"
                );

                console.add(
                        "fov <number>"
                );

                console.add(
                        "speed <number>"
                );

                console.add(
                        "jump <number>"
                );

                console.add(
                        "invert"
                );

                console.add(
                        "view"
                );

                console.add(
                        "pos"
                );

                console.add(
                        "reset"
                );

                console.add(
                        "hallway"
                );

                console.add(
                        "room"
                );

                console.add(
                        "loadmap <path>"
                );

            } else if (
                    lower.equals(
                            "hallway"
                    )
            ) {

                enterHallwayMode();

            } else if (
                    lower.equals(
                            "room"
                    )
            ) {

                exitHallwayMode();

            } else if (
                    lower.startsWith(
                            "loadmap "
                    )
            ) {

                String path = command.substring(8).trim();
                loadMapJson(path);

            } else if (
                    lower.startsWith(
                            "sensitivity "
                    )
            ) {

                sensitivity =
                        Float.parseFloat(
                                command
                                        .substring(
                                                12
                                        )
                                        .trim()
                        );

                sensitivity =
                        clamp(
                                sensitivity,
                                SENS_MIN,
                                SENS_MAX
                        );

                console.add(
                        String.format(
                                "sensitivity %.2f",
                                sensitivity
                        )
                );

            } else if (
                    lower.startsWith(
                            "fov "
                    )
            ) {

                fov =
                        Float.parseFloat(
                                command
                                        .substring(
                                                4
                                        )
                                        .trim()
                        );

                fov =
                        clamp(
                                fov,
                                FOV_MIN,
                                FOV_MAX
                        );

                console.add(
                        String.format(
                                "fov %.0f",
                                fov
                        )
                );

            } else if (
                    lower.startsWith(
                            "speed "
                    )
            ) {

                forwardSpeed =
                        Float.parseFloat(
                                command
                                        .substring(
                                                6
                                        )
                                        .trim()
                        );

                forwardSpeed =
                        clamp(
                                forwardSpeed,
                                SPEED_MIN,
                                SPEED_MAX
                        );

                sideSpeed =
                        forwardSpeed * 0.68f;

                console.add(
                        String.format(
                                "speed %.1f",
                                forwardSpeed
                        )
                );

            } else if (
                    lower.startsWith(
                            "jump "
                    )
            ) {

                jumpSpeed =
                        Float.parseFloat(
                                command
                                        .substring(
                                                5
                                        )
                                        .trim()
                        );

                jumpSpeed =
                        clamp(
                                jumpSpeed,
                                JUMP_MIN,
                                JUMP_MAX
                        );

                console.add(
                        String.format(
                                "jump %.1f",
                                jumpSpeed
                        )
                );

            } else if (
                    lower.equals(
                            "invert"
                    )
            ) {

                invertMouse =
                        !invertMouse;

                console.add(
                        "invert "
                                +
                                (
                                        invertMouse
                                                ? "1"
                                                : "0"
                                )
                );

            } else if (
                    lower.equals(
                            "view"
                    )
            ) {

                console.add(
                        String.format(
                                "yaw %.2f pitch %.2f fov %.2f",
                                yaw,
                                pitch,
                                fov
                        )
                );

            } else if (
                    lower.equals(
                            "pos"
                    )
            ) {

                console.add(
                        String.format(
                                "position %.2f %.2f %.2f",
                                x,
                                y,
                                z
                        )
                );

            } else if (
                    lower.equals(
                            "reset"
                    )
            ) {

                if (
                        hallwayMode
                ) {

                    enterHallwayMode();

                } else {

                    resetPlayer();
                }

                console.add(
                        "player reset"
                );

            } else {

                console.add(
                        "unknown command: "
                                +
                                command
                );
            }

        } catch (
                Exception exception
        ) {

            console.add(
                    "invalid command"
            );
        }

        while (
                console.size() > 32
        ) {

            console.remove(
                    0
            );
        }

        consoleInput =
                "";
    }

    // ============================================================
    // KEYBOARD
    // ============================================================

    boolean down(
            int key
    ) {

        return
                key >= 0
                &&
                key < keys.length
                &&
                keys[key];
    }

    @Override
    public void keyPressed(
            KeyEvent e
    ) {

        int key =
                e.getKeyCode();

        if (
                key >= 0
                &&
                key < keys.length
        ) {

            keys[key] =
                    true;
        }

        if (
                key == KeyEvent.VK_ESCAPE
        ) {

            if (
                    consoleOpen
            ) {

                consoleOpen =
                        false;

                consoleInput =
                        "";

                captureMouse();

            } else if (
                    menuOpen
            ) {

                menuOpen =
                        false;

                captureMouse();

            } else {

                menuOpen =
                        true;

                releaseMouse();
            }

            return;
        }

        if (menuOpen) {
            handleMenuKey(e);
            return;
        }

        if (
                key == KeyEvent.VK_BACK_QUOTE
        ) {

            consoleOpen =
                    !consoleOpen;

            if (
                    consoleOpen
            ) {

                menuOpen =
                        false;

                releaseMouse();

            } else {

                captureMouse();
            }

            return;
        }

        if (
                consoleOpen
        ) {

            handleConsoleKey(
                    e
            );
        }
    }

    @Override
    public void keyReleased(
            KeyEvent e
    ) {

        int key =
                e.getKeyCode();

        if (
                key >= 0
                &&
                key < keys.length
        ) {

            keys[key] =
                    false;
        }
    }

    @Override
    public void keyTyped(
            KeyEvent e
    ) {

        if (
                !consoleOpen
        ) {

            return;
        }

        char c =
                e.getKeyChar();

        if (
                c == '\n'
                ||
                c == '\r'
        ) {

            return;
        }

        if (
                !Character.isISOControl(
                        c
                )
        ) {

            consoleInput +=
                    c;
        }
    }

    // ============================================================
    // CONSOLE KEY HANDLING
    // ============================================================

    void handleConsoleKey(
            KeyEvent e
    ) {

        if (
                e.getKeyCode()
                ==
                KeyEvent.VK_BACK_SPACE
        ) {

            if (
                    !consoleInput.isEmpty()
            ) {

                consoleInput =
                        consoleInput.substring(
                                0,
                                consoleInput.length() - 1
                        );
            }
        }

        if (
                e.getKeyCode()
                ==
                KeyEvent.VK_ENTER
        ) {

            executeConsoleCommand();
        }
    }

    // ============================================================
    // MENU KEY HANDLING
    // ============================================================

    void handleMenuKey(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyEvent.VK_UP) {
            menuSelection--;
            if (menuSelection < 0) menuSelection = 6;
        } else if (key == KeyEvent.VK_DOWN) {
            menuSelection++;
            if (menuSelection > 6) menuSelection = 0;
        } else if (key == KeyEvent.VK_LEFT) {
            if (menuSelection == 0) {
                sensitivity = Math.max(SENS_MIN, sensitivity - 0.05f);
            } else if (menuSelection == 1) {
                fov = Math.max(FOV_MIN, fov - 5.0f);
            } else if (menuSelection == 2) {
                forwardSpeed = Math.max(SPEED_MIN, forwardSpeed - 0.5f);
                sideSpeed = forwardSpeed * 0.68f;
            } else if (menuSelection == 3) {
                jumpSpeed = Math.max(JUMP_MIN, jumpSpeed - 0.5f);
            }
        } else if (key == KeyEvent.VK_RIGHT) {
            if (menuSelection == 0) {
                sensitivity = Math.min(SENS_MAX, sensitivity + 0.05f);
            } else if (menuSelection == 1) {
                fov = Math.min(FOV_MAX, fov + 5.0f);
            } else if (menuSelection == 2) {
                forwardSpeed = Math.min(SPEED_MAX, forwardSpeed + 0.5f);
                sideSpeed = forwardSpeed * 0.68f;
            } else if (menuSelection == 3) {
                jumpSpeed = Math.min(JUMP_MAX, jumpSpeed + 0.5f);
            }
        } else if (key == KeyEvent.VK_ENTER) {
            if (menuSelection == 4) {
                invertMouse = !invertMouse;
            } else if (menuSelection == 5) {
                SwingUtilities.invokeLater(this::promptAndLoadMap);
            } else if (menuSelection == 6) {
                menuOpen = false;
                captureMouse();
            }
        }
    }

    // ============================================================
    // MOUSE CAPTURE
    // ============================================================

    void captureMouse() {

        if (
                menuOpen
                ||
                consoleOpen
        ) {

            return;
        }

        mouseCaptured =
                true;

        accumulatedDX =
                0.0f;

        accumulatedDY =
                0.0f;

        rawDX =
                0;

        rawDY =
                0;

        mouseSpeed =
                0.0f;

        lastMouseNanos =
                0L;

        haveMousePosition =
                false;

        try {

            BufferedImage cursorImage =
                    new BufferedImage(
                            16,
                            16,
                            BufferedImage.TYPE_INT_ARGB
                    );

            Cursor invisibleCursor =
                    Toolkit
                            .getDefaultToolkit()
                            .createCustomCursor(
                                    cursorImage,
                                    new java.awt.Point(
                                            0,
                                            0
                                    ),
                                    "laserpointere-invisible"
                            );

            setCursor(
                    invisibleCursor
            );

        } catch (
                Exception ignored
        ) {
        }

        requestFocusInWindow();

        recenterCursor();
    }

    void releaseMouse() {

        mouseCaptured =
                false;

        accumulatedDX =
                0.0f;

        accumulatedDY =
                0.0f;

        rawDX =
                0;

        rawDY =
                0;

        mouseSpeed =
                0.0f;

        lastMouseNanos =
                0L;

        haveMousePosition =
                false;

        setCursor(
                Cursor.getDefaultCursor()
        );
    }

    // ============================================================
    // MOUSE MOVEMENT
    // ============================================================

    @Override
    public void mouseMoved(
            MouseEvent e
    ) {

        processMouseMotion(
                e
        );
    }

    @Override
    public void mouseDragged(
            MouseEvent e
    ) {

        processMouseMotion(
                e
        );
    }

    void processMouseMotion(
            MouseEvent e
    ) {

        int mx =
                e.getX();

        int my =
                e.getY();

        mouseX =
                mx;

        mouseY =
                my;

        if (
                !mouseCaptured
        ) {

            lastMouseX =
                    mx;

            lastMouseY =
                    my;

            haveMousePosition =
                    true;

            return;
        }

        if (
                suppressNextMotion
        ) {

            suppressNextMotion =
                    false;

            return;
        }

        int centerX =
                W / 2;

        int centerY =
                H / 2;

        int dx =
                mx - centerX;

        int dy =
                my - centerY;

        rawDX =
                dx;

        rawDY =
                dy;

        accumulatedDX +=
                dx;

        accumulatedDY +=
                dy;

        long now =
                System.nanoTime();

        if (
                lastMouseNanos != 0L
        ) {

            double dt =
                    (
                            now
                            -
                            lastMouseNanos
                    )
                    /
                    1_000_000_000.0;

            if (
                    dt > 0.0
                    &&
                    dt < 1.0
            ) {

                mouseSpeed =
                        (float)(
                                Math.hypot(
                                        dx,
                                        dy
                                )
                                /
                                dt
                        );
            }
        }

        lastMouseNanos =
                now;

        recenterCursor();
    }

    void recenterCursor() {

        if (
                robot == null
        ) {

            try {

                robot =
                        new Robot();

            } catch (
                    Exception e
            ) {

                return;
            }
        }

        try {

            java.awt.Point topLeft =
                    getLocationOnScreen();

            int screenX =
                    topLeft.x + W / 2;

            int screenY =
                    topLeft.y + H / 2;

            suppressNextMotion =
                    true;

            robot.mouseMove(
                    screenX,
                    screenY
            );

        } catch (
                Exception e
        ) {

            suppressNextMotion =
                    false;
        }
    }

    // ============================================================
    // MOUSE BUTTONS
    // ============================================================

    @Override
    public void mousePressed(
            MouseEvent e
    ) {

        requestFocusInWindow();

        if (
                !menuOpen
                &&
                !consoleOpen
        ) {

            captureMouse();
        }
    }

    @Override
    public void mouseReleased(
            MouseEvent e
    ) {
    }

    @Override
    public void mouseClicked(
            MouseEvent e
    ) {
    }

    @Override
    public void mouseEntered(
            MouseEvent e
    ) {
    }

    @Override
    public void mouseExited(
            MouseEvent e
    ) {
    }

    void promptAndLoadMap() {
        FileDialog fd = new FileDialog((Frame)null, "Open Map JSON", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".json"));
        fd.setVisible(true);
        
        String filename = fd.getFile();
        String directory = fd.getDirectory();
        
        if (filename != null && directory != null) {
            String fullPath = directory + filename;
            loadMapJson(fullPath);
        }
    }

    // ============================================================
    // MAP & JSON LOADING
    // ============================================================

    void loadMapJson(String filePath) {
        walls.clear();
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            String[] items = content.split("\\{");
            for (String item : items) {
                if (item.contains("x1")) {
                    double x1 = extractJsonValue(item, "x1");
                    double y1 = extractJsonValue(item, "y1");
                    double x2 = extractJsonValue(item, "x2");
                    double y2 = extractJsonValue(item, "y2");
                    int id = (int) extractJsonValue(item, "id");
                    
                    walls.add(new Wall(x1, y1, x2, y2, id));
                }
            }
            buildBSP();
            resetPlayer();
            hallwayMode = false;
            console.add("Loaded " + walls.size() + " walls from " + filePath);
        } catch (Exception e) {
            console.add("Failed to load map file: " + e.getMessage());
            System.err.println("Failed to load map file: " + e.getMessage());
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

    void buildMap() {

        walls.clear();

        walls.add(
                new Wall(
                        0,
                        0,
                        12,
                        0,
                        0
                )
        );

        walls.add(
                new Wall(
                        12,
                        0,
                        12,
                        9,
                        1
                )
        );

        walls.add(
                new Wall(
                        12,
                        9,
                        0,
                        9,
                        2
                )
        );

        walls.add(
                new Wall(
                        0,
                        9,
                        0,
                        0,
                        3
                )
        );

        walls.add(
                new Wall(
                        5,
                        0,
                        5,
                        5,
                        4
                )
        );

        walls.add(
                new Wall(
                        5,
                        5,
                        8,
                        5,
                        5
                )
        );

        walls.add(
                new Wall(
                        8,
                        5,
                        8,
                        9,
                        6
                )
        );

        walls.add(
                new Wall(
                        9,
                        0,
                        9,
                        2.5,
                        7
                )
        );

        walls.add(
                new Wall(
                        9,
                        2.5,
                        11,
                        2.5,
                        8
                )
        );

        walls.add(
                new Wall(
                        11,
                        2.5,
                        11,
                        0,
                        9
                )
        );
    }

    // ============================================================
    // PROCEDURAL INFINITE HALLWAY
    // ============================================================

    static final double HALLWAY_WIDTH = 4.0;
    static final double HALLWAY_CHUNK_LENGTH = 6.0;
    static final int HALLWAY_CHUNKS_AHEAD = 8;
    static final int HALLWAY_CHUNKS_BEHIND = 3;

    boolean hallwayMode = false;

    int hallwayMaxChunk = -1;
    int hallwayMinChunk = 0;

    void enterHallwayMode() {

        hallwayMode =
                true;

        walls.clear();

        hallwayMaxChunk =
                -1;

        hallwayMinChunk =
                0;

        x =
                (float)(HALLWAY_CHUNK_LENGTH * 0.5);

        y =
                (float)(HALLWAY_WIDTH * 0.5);

        z =
                0.0f;

        yaw =
                0.0f;

        pitch =
                0.0f;

        verticalVelocity =
                0.0f;

        onGround =
                true;

        ensureHallwayChunks();

        console.add(
                "Entered procedural infinite hallway"
        );
    }

    void exitHallwayMode() {

        hallwayMode =
                false;

        buildMap();

        buildBSP();

        resetPlayer();

        console.add(
                "Returned to test room"
        );
    }

    void ensureHallwayChunks() {

        if (
                !hallwayMode
        ) {

            return;
        }

        int currentChunk =
                (int)Math.floor(
                        x / HALLWAY_CHUNK_LENGTH
                );

        int wantMaxChunk =
                currentChunk + HALLWAY_CHUNKS_AHEAD;

        int wantMinChunk =
                currentChunk - HALLWAY_CHUNKS_BEHIND;

        boolean changed =
                false;

        while (
                hallwayMaxChunk < wantMaxChunk
        ) {

            hallwayMaxChunk++;

            generateHallwayChunk(
                    hallwayMaxChunk
            );

            changed =
                    true;
        }

        if (
                wantMinChunk > hallwayMinChunk
        ) {

            final int pruneBefore =
                    wantMinChunk;

            walls.removeIf(
                    w -> w.chunk != -1
                            && w.chunk < pruneBefore
            );

            hallwayMinChunk =
                    wantMinChunk;

            changed =
                    true;
        }

        if (
                changed
        ) {

            buildBSP();
        }
    }

    void generateHallwayChunk(
            int chunkIndex
    ) {

        double startX =
                chunkIndex * HALLWAY_CHUNK_LENGTH;

        double endX =
                startX + HALLWAY_CHUNK_LENGTH;

        int baseId =
                Math.floorMod(
                        chunkIndex,
                        997
                )
                * 8;

        walls.add(
                new Wall(
                        startX,
                        0.0,
                        endX,
                        0.0,
                        baseId,
                        chunkIndex
                )
        );

        walls.add(
                new Wall(
                        startX,
                        HALLWAY_WIDTH,
                        endX,
                        HALLWAY_WIDTH,
                        baseId + 1,
                        chunkIndex
                )
        );

        java.util.Random rng =
                new java.util.Random(
                        chunkIndex * 2654435761L
                        + 0x9E3779B9L
                );

        if (
                Math.floorMod(
                        chunkIndex,
                        3
                )
                == 0
        ) {

            boolean leftSide =
                    rng.nextBoolean();

            double notchDepth =
                    1.0 + rng.nextDouble() * 0.8;

            double notchStart =
                    startX + 1.5;

            double notchEnd =
                    startX + HALLWAY_CHUNK_LENGTH - 1.5;

            double baseY =
                    leftSide ? 0.0 : HALLWAY_WIDTH;

            double outY =
                    leftSide
                            ? -notchDepth
                            : HALLWAY_WIDTH + notchDepth;

            walls.add(
                    new Wall(
                            notchStart,
                            baseY,
                            notchStart,
                            outY,
                            baseId + 2,
                            chunkIndex
                    )
            );

            walls.add(
                    new Wall(
                            notchStart,
                            outY,
                            notchEnd,
                            outY,
                            baseId + 3,
                            chunkIndex
                    )
            );

            walls.add(
                    new Wall(
                            notchEnd,
                            outY,
                            notchEnd,
                            baseY,
                            baseId + 4,
                            chunkIndex
                    )
            );
        }
    }

    // ============================================================
    // BSP TREE
    // ============================================================

    static final double BSP_EPS = 1e-6;

    void buildBSP() {

        bspNodeCount = 0;
        bspSplitCount = 0;

        bspRoot =
                buildBSPNode(
                        new ArrayList<>(walls)
                );
    }

    BSPNode buildBSPNode(
            List<Wall> region
    ) {

        if (
                region.isEmpty()
        ) {

            return null;
        }

        Wall partition =
                choosePartition(
                        region
                );

        List<Wall> coincident =
                new ArrayList<>();

        List<Wall> front =
                new ArrayList<>();

        List<Wall> back =
                new ArrayList<>();

        coincident.add(
                partition
        );

        for (
                Wall wall :
                region
        ) {

            if (
                    wall == partition
            ) {

                continue;
            }

            classifyAndBucket(
                    wall,
                    partition,
                    front,
                    back,
                    coincident
            );
        }

        BSPNode node =
                new BSPNode();

        node.wall =
                partition;

        node.coincident =
                coincident;

        bspNodeCount++;

        node.front =
                buildBSPNode(
                        front
                );

        node.back =
                buildBSPNode(
                        back
                );

        return node;
    }

    Wall choosePartition(
            List<Wall> region
    ) {

        Wall best =
                region.get(0);

        int bestSplits =
                Integer.MAX_VALUE;

        for (
                Wall candidate :
                region
        ) {

            int splits =
                    0;

            for (
                    Wall other :
                    region
            ) {

                if (
                        other == candidate
                ) {

                    continue;
                }

                double sideA =
                        side(
                                other.x1,
                                other.y1,
                                candidate
                        );

                double sideB =
                        side(
                                other.x2,
                                other.y2,
                                candidate
                        );

                boolean frontA =
                        sideA > BSP_EPS;

                boolean backA =
                        sideA < -BSP_EPS;

                boolean frontB =
                        sideB > BSP_EPS;

                boolean backB =
                        sideB < -BSP_EPS;

                if (
                        (
                                frontA
                                &&
                                backB
                        )
                        ||
                        (
                                backA
                                &&
                                frontB
                        )
                ) {

                    splits++;
                }
            }

            if (
                    splits < bestSplits
            ) {

                bestSplits =
                        splits;

                best =
                        candidate;
            }
        }

        return best;
    }

    double side(
            double px,
            double py,
            Wall partition
    ) {

        double dx =
                partition.x2 - partition.x1;

        double dy =
                partition.y2 - partition.y1;

        return dx * (py - partition.y1)
                - dy * (px - partition.x1);
    }

    void classifyAndBucket(
            Wall wall,
            Wall partition,
            List<Wall> front,
            List<Wall> back,
            List<Wall> coincident
    ) {

        double sideA =
                side(
                        wall.x1,
                        wall.y1,
                        partition
                );

        double sideB =
                side(
                        wall.x2,
                        wall.y2,
                        partition
                );

        boolean frontA =
                sideA > BSP_EPS;

        boolean backA =
                sideA < -BSP_EPS;

        boolean frontB =
                sideB > BSP_EPS;

        boolean backB =
                sideB < -BSP_EPS;

        if (
                !frontA
                &&
                !backA
                &&
                !frontB
                &&
                !backB
        ) {

            coincident.add(
                    wall
            );

            return;
        }

        boolean crosses =
                (
                        frontA
                        &&
                        backB
                )
                ||
                (
                        backA
                        &&
                        frontB
                );

        if (
                !crosses
        ) {

            boolean isFront =
                    frontA
                    ||
                    frontB;

            if (
                    isFront
            ) {

                front.add(
                        wall
                );

            } else {

                back.add(
                        wall
                );
            }

            return;
        }

        bspSplitCount++;

        double dxW =
                wall.x2 - wall.x1;

        double dyW =
                wall.y2 - wall.y1;

        double dxP =
                partition.x2 - partition.x1;

        double dyP =
                partition.y2 - partition.y1;

        double denom =
                dxW * dyP - dyW * dxP;

        double t =
                0.5;

        if (
                Math.abs(denom) > BSP_EPS
        ) {

            t =
                    (
                            (partition.x1 - wall.x1) * dyP
                            -
                            (partition.y1 - wall.y1) * dxP
                    )
                    /
                    denom;
        }

        double ix =
                wall.x1 + t * dxW;

        double iy =
                wall.y1 + t * dyW;

        Wall partA =
                new Wall(
                        wall.x1,
                        wall.y1,
                        ix,
                        iy,
                        wall.id
                );

        Wall partB =
                new Wall(
                        ix,
                        iy,
                        wall.x2,
                        wall.y2,
                        wall.id
                );

        if (
                frontA
        ) {

            front.add(
                    partA
            );

            back.add(
                    partB
            );

        } else {

            back.add(
                    partA
            );

            front.add(
                    partB
            );
        }
    }

    void traverseBSP(
            BSPNode node,
            double camX,
            double camY,
            List<Wall> out
    ) {

        if (
                node == null
        ) {

            return;
        }

        double camSide =
                side(
                        camX,
                        camY,
                        node.wall
                );

        BSPNode near =
                camSide >= 0
                        ? node.front
                        : node.back;

        BSPNode far =
                camSide >= 0
                        ? node.back
                        : node.front;

        traverseBSP(
                far,
                camX,
                camY,
                out
        );

        out.addAll(
                node.coincident
        );

        traverseBSP(
                near,
                camX,
                camY,
                out
        );
    }

    // ============================================================
    // RESET
    // ============================================================

    void resetPlayer() {

        x =
                2.0f;

        y =
                2.0f;

        z =
                0.0f;

        yaw =
                0.0f;

        pitch =
                0.0f;

        verticalVelocity =
                0.0f;

        onGround =
                true;
    }

    // ============================================================
    // UTILITY
    // ============================================================

    float clamp(
            float value,
            float min,
            float max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    // ============================================================
    // USER COMMAND
    // ============================================================

    static class UserCmd {

        float forward;
        float side;
        float up;
    }

    // ============================================================
    // BSP NODE
    // ============================================================

    static class BSPNode {

        Wall wall;

        List<Wall> coincident;

        BSPNode front;
        BSPNode back;
    }

    // ============================================================
    // WORLD WALL
    // ============================================================

    static class Wall {

        double x1;
        double y1;

        double x2;
        double y2;

        int id;

        int chunk = -1;

        Wall(
                double x1,
                double y1,
                double x2,
                double y2,
                int id
        ) {

            this(
                    x1,
                    y1,
                    x2,
                    y2,
                    id,
                    -1
            );
        }

        Wall(
                double x1,
                double y1,
                double x2,
                double y2,
                int id,
                int chunk
        ) {

            this.x1 =
                    x1;

            this.y1 =
                    y1;

            this.x2 =
                    x2;

            this.y2 =
                    y2;

            this.id =
                    id;

            this.chunk =
                    chunk;
        }
    }

    // ============================================================
    // PROJECTED POINT
    // ============================================================

    static class Point3 {

        int x;
        int y;

        double depth;

        Point3(
                int x,
                int y,
                double depth
        ) {

            this.x =
                    x;

            this.y =
                    y;

            this.depth =
                    depth;
            }
    }

    // ============================================================
    // PROJECTED WALL
    // ============================================================

    static class Projection {

        Point3 bl;
        Point3 br;

        Point3 tr;
        Point3 tl;

        double depth;

        int id;

        Projection(
                Point3 bl,
                Point3 br,
                Point3 tr,
                Point3 tl,
                double depth,
                int id
        ) {

            this.bl =
                    bl;

            this.br =
                    br;

            this.tr =
                    tr;

            this.tl =
                    tl;

            this.depth =
                    depth;

            this.id =
                    id;
        }
    }
}