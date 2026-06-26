import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.Timer;

public class PacMan extends JPanel implements ActionListener, KeyListener {

    // ── Inner class: Block (entity) ───────────────────────────────────────────
    class Block {
        int x, y, width, height;
        Image image;
        int startX, startY;
        char direction = 'U';
        int velocityX = 0, velocityY = 0;

        Block(Image image, int x, int y, int width, int height) {
            this.image  = image;
            this.x      = x; this.startX = x;
            this.y      = y; this.startY = y;
            this.width  = width;
            this.height = height;
        }

        void updateDirection(char dir) {
            char prev = this.direction;
            if(this.direction!=dir){
    stats.recordDirectionChange();
}
            this.direction = dir;
            updateVelocity();
            this.x += this.velocityX;
            this.y += this.velocityY;
            for (Block wall : walls) {
                if (collision(this, wall)) {
                    this.x -= this.velocityX;
                    this.y -= this.velocityY;
                    this.direction = prev;
                    updateVelocity();
                    break;
                }
            }
        }

        void updateVelocity() {
            int spd = tileSize / 4;
            velocityX = 0; velocityY = 0;
            switch (direction) {
                case 'U' -> velocityY = -spd;
                case 'D' -> velocityY =  spd;
                case 'L' -> velocityX = -spd;
                case 'R' -> velocityX =  spd;
            }
        }

        void reset() { x = startX; y = startY; }
    }

    // ── Ghost subclass — override getTarget() for each personality ────────────
    class Ghost extends Block {
        String personality; // "blinky","pinky","inky","clyde"
        boolean scared   = false;
        boolean flashing = false;  // scared-about-to-end
        int scaredTimer  = 0;
        static final int SCARED_DURATION = 200; // ticks (~10 s at 20 fps)

        Ghost(Image image, int x, int y, String personality) {
            super(image, x, y, tileSize, tileSize);
            this.personality = personality;
        }

        // Each subclass personality targets differently — pure polymorphism for interviews
        int[] getTarget() {
            int px = pacman.x / tileSize;
            int py = pacman.y / tileSize;
            return switch (personality) {
                // Blinky: directly chases Pac-Man
                case "blinky" -> new int[]{px, py};
                // Pinky: 4 tiles ahead of Pac-Man
                case "pinky"  -> {
                    int tx = px, ty = py;
                    if      (pacman.direction == 'U') ty -= 4;
                    else if (pacman.direction == 'D') ty += 4;
                    else if (pacman.direction == 'L') tx -= 4;
                    else if (pacman.direction == 'R') tx += 4;
                    yield new int[]{tx, ty};
                }
                // Inky: targets opposite side of map from Pac-Man (flanking)
                case "inky"   -> new int[]{columnCount - 1 - px, rowCount - 1 - py};
                // Clyde: if close, scatter to bottom-left corner; else chase
                case "clyde"  -> {
                    int gx = x / tileSize, gy = y / tileSize;
                    double dist = Math.hypot(gx - px, gy - py);
                    yield dist < 8 ? new int[]{1, rowCount - 2} : new int[]{px, py};
                }
                default -> new int[]{px, py};
            };
        }

        void scare() {
            scared = true;
            flashing = false;
            scaredTimer = SCARED_DURATION;
        }

        void tickScared() {
            if (!scared) return;
            scaredTimer--;
            flashing = scaredTimer < 60; // flash in last 3 seconds
            if (scaredTimer <= 0) {
                scared   = false;
                flashing = false;
                image    = normalImage;
            }
        }

        Image normalImage; // set after construction
    }
    Statistics stats = new Statistics();

boolean USE_BFS = true;
    // ── Board constants ───────────────────────────────────────────────────────
    private static final int ROW_COUNT    = 21;
    private static final int COLUMN_COUNT = 19;
    private static final int TILE_SIZE    = 32;

    private final int rowCount    = ROW_COUNT;
    private final int columnCount = COLUMN_COUNT;
    private final int tileSize    = TILE_SIZE;
    private final int boardWidth  = columnCount * tileSize;
    private final int boardHeight = rowCount    * tileSize;

    // ── Tile map ──────────────────────────────────────────────────────────────
    // X=wall O=skip P=pacman ' '=food Q=power-pellet
    // b=blue o=orange p=pink r=red
    private final String[] tileMap = {
        "XXXXXXXXXXXXXXXXXXX",
        "X        X        X",
        "X XX XXX X XXX XX X",
        "X                 X",
        "X XX X XXXXX X XX X",
        "X    X       X    X",
        "XXXX XXXX XXXX XXXX",
        "OOOX X       X XOOO",
        "XXXX X XXrXX X XXXX",
        "O      bpo        O",
        "XXXX X XXXXX X XXXX",
        "OOOX X       X XOOO",
        "XXXX X XXXXX X XXXX",
        "X         X       X",
        "X XX XXX X XXX XX X",
        "X  X   P   X      X",
        "XX X X XXXXX X X XX",
        "X    X   X   X    X",
        "X XXXXXX X XXXXXX X",
        "X                 X",
        "XXXXXXXXXXXXXXXXXXX"
    };

    // power pellet positions (corners — classic Pac-Man)
    private final int[][] powerPelletPositions = {{1,1},{17,1},{1,19},{17,19}};

    // ── Images ────────────────────────────────────────────────────────────────
    private Image wallImage;
    private Image blueGhostImage, orangeGhostImage, pinkGhostImage, redGhostImage;
    private Image scaredGhostImage, flashGhostImage;
    private Image pacmanUpImage, pacmanDownImage, pacmanLeftImage, pacmanRightImage;

    // ── Game entities ─────────────────────────────────────────────────────────
    HashSet<Block>  walls  = new HashSet<>();
    HashSet<Block>  foods  = new HashSet<>();
    HashSet<Block>  powerPellets = new HashSet<>();
    HashSet<Ghost>  ghosts = new HashSet<>();
    Block pacman;

    // ── Game state ────────────────────────────────────────────────────────────
    Timer gameLoop;
    int score    = 0;
    int highScore= 0;
    int lives    = 3;
    boolean gameOver = false;
    int ghostEatMultiplier = 1; // doubles per ghost eaten in one power-pellet

    private static final String HIGH_SCORE_FILE = "highscore.txt";

    // ── Constructor ───────────────────────────────────────────────────────────
    PacMan() {
        setPreferredSize(new Dimension(boardWidth, boardHeight));
        setBackground(Color.BLACK);
        addKeyListener(this);
        setFocusable(true);

        loadImages();
        loadHighScore();
        loadMap();
        startGhosts();
        stats.start();

        gameLoop = new Timer(50, this); // 20 fps
        gameLoop.start();
    }

    private void loadImages() {
        wallImage       = loadImg("wall.png");
        blueGhostImage  = loadImg("blueGhost.png");
        orangeGhostImage= loadImg("orangeGhost.png");
        pinkGhostImage  = loadImg("pinkGhost.png");
        redGhostImage   = loadImg("redGhost.png");
        scaredGhostImage= loadImg("scaredGhost.png");
        // re-use blueGhost as flash image if you don't have a separate one
        flashGhostImage = loadImg("scaredGhost.png");
        pacmanUpImage   = loadImg("pacmanUp.png");
        pacmanDownImage = loadImg("pacmanDown.png");
        pacmanLeftImage = loadImg("pacmanLeft.png");
        pacmanRightImage= loadImg("pacmanRight.png");
    }

    private Image loadImg(String name) {
        return new ImageIcon(getClass().getResource("./" + name)).getImage();
    }

    // ── Map loading ───────────────────────────────────────────────────────────
    public void loadMap() {
        walls.clear(); foods.clear(); ghosts.clear(); powerPellets.clear();

        for (int r = 0; r < rowCount; r++) {
            String row = tileMap[r];
            for (int c = 0; c < columnCount; c++) {
                int x = c * tileSize, y = r * tileSize;
                char ch = row.charAt(c);
                switch (ch) {
                    case 'X' -> walls.add(new Block(wallImage, x, y, tileSize, tileSize));
                    case 'b' -> addGhost(blueGhostImage,   x, y, "inky");
                    case 'o' -> addGhost(orangeGhostImage, x, y, "clyde");
                    case 'p' -> addGhost(pinkGhostImage,   x, y, "pinky");
                    case 'r' -> addGhost(redGhostImage,    x, y, "blinky");
                    case 'P' -> pacman = new Block(pacmanRightImage, x, y, tileSize, tileSize);
                    case ' ' -> foods.add(new Block(null, x + 14, y + 14, 4, 4));
                }
            }
        }
        // Add power pellets at the four corners
        for (int[] pos : powerPelletPositions) {
            int px = pos[0] * tileSize + 8;
            int py = pos[1] * tileSize + 8;
            powerPellets.add(new Block(null, px, py, 16, 16));
        }
    }

    private void addGhost(Image img, int x, int y, String personality) {
        Ghost g = new Ghost(img, x, y, personality);
        g.normalImage = img;
        ghosts.add(g);
    }

    private void startGhosts() {
        char[] dirs = {'U','D','L','R'};
        Random rnd = new Random();
        for (Ghost g : ghosts) g.updateDirection(dirs[rnd.nextInt(4)]);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    private void draw(Graphics g) {
        // Pac-Man
        g.drawImage(pacman.image, pacman.x, pacman.y, pacman.width, pacman.height, null);

        // Ghosts
        for (Ghost ghost : ghosts) {
            Image img;
            if (ghost.scared) {
                img = (ghost.flashing && (System.currentTimeMillis() / 200 % 2 == 0))
                      ? flashGhostImage : scaredGhostImage;
            } else {
                img = ghost.normalImage;
            }
            g.drawImage(img, ghost.x, ghost.y, ghost.width, ghost.height, null);
        }

        // Walls
        for (Block wall : walls)
            g.drawImage(wall.image, wall.x, wall.y, wall.width, wall.height, null);

        // Food dots
        g.setColor(Color.WHITE);
        for (Block food : foods)
            g.fillRect(food.x, food.y, food.width, food.height);

        // Power pellets (yellow, pulsing size)
        g.setColor(Color.YELLOW);
        for (Block pp : powerPellets)
            g.fillOval(pp.x, pp.y, pp.width, pp.height);

        // HUD
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        g.setColor(Color.WHITE);
        if (gameOver) {
            g.setColor(Color.RED);
            g.drawString("GAME OVER  Score: " + score, tileSize / 2, tileSize / 2);
            g.setColor(Color.YELLOW);
            g.drawString("High Score: " + highScore, tileSize / 2, tileSize);
            g.setColor(Color.WHITE);
            g.drawString("Press any arrow key to restart", tileSize / 2, tileSize * 3 / 2);
        } else {
            g.drawString("Lives: " + lives + "   Score: " + score + "   Best: " + highScore,
                         tileSize / 2, tileSize / 2);
        }
    }

    // ── Movement & logic ──────────────────────────────────────────────────────
    public void move() {
        // ── Pac-Man movement ──
        pacman.x += pacman.velocityX;
        pacman.y += pacman.velocityY;

        // Tunnel wrap (FIX for the known bug)
        if (pacman.x + pacman.width < 0)           pacman.x = boardWidth;
        else if (pacman.x > boardWidth)             pacman.x = -pacman.width;

        for (Block wall : walls) {
            if (collision(pacman, wall)) {
                pacman.x -= pacman.velocityX;
                pacman.y -= pacman.velocityY;
                break;
            }
        }

        // ── Power pellet check ──
        Block eaten = null;
        for (Block pp : powerPellets) {
            if (collision(pacman, pp)) { eaten = pp; break; }
        }
        if (eaten != null) {
            powerPellets.remove(eaten);
            ghostEatMultiplier = 1;
            for (Ghost g : ghosts) g.scare();
        }

        // ── Ghost movement (BFS pathfinding) ──
        for (Ghost ghost : ghosts) {
            double distance = Math.hypot(
        ghost.x-pacman.x,
        ghost.y-pacman.y);
stats.recordDistance(distance);

            ghost.tickScared();

            if (collision(ghost, pacman)) {
                handleGhostCollision(ghost);
                if (gameOver) return;
                continue;
            }

            // Force ghosts out of the ghost house row (row 9)
            if (ghost.y == tileSize * 9 && ghost.direction != 'U' && ghost.direction != 'D')
                ghost.updateDirection('U');

            // Move ghost
            ghost.x += ghost.velocityX;
            ghost.y += ghost.velocityY;

            // Tunnel wrap for ghosts too
            if (ghost.x + ghost.width < 0)   ghost.x = boardWidth;
            else if (ghost.x > boardWidth)    ghost.x = -ghost.width;

            // Wall collision → BFS to find new direction
           // Check wall collisions
for (Block wall : walls) {

    if (collision(ghost, wall)) {

        ghost.x -= ghost.velocityX;
        ghost.y -= ghost.velocityY;

        stats.recordWallHit();

        break;
    }
}

// Every time the ghost reaches a new tile,
// compute the next direction.
if (ghost.x % tileSize == 0 &&
    ghost.y % tileSize == 0) {

    char nextDirection;

    if (ghost.scared) {

        nextDirection = randomDir(ghost);

    }
    else if (USE_BFS) {

        nextDirection = bfsDirection(
                ghost,
                ghost.getTarget());

    }
    else {

        nextDirection = randomDir(ghost);

    }

    ghost.updateDirection(nextDirection);
}
        }

        // ── Food collection ──
        Block foodEaten = null;
        for (Block food : foods) {
            if (collision(pacman, food)) { foodEaten = food; break; }
        }
        if (foodEaten != null) {
            foods.remove(foodEaten);
            score += 10;
        }

        // Win condition
        if (foods.isEmpty() && powerPellets.isEmpty()) {
            loadMap();
            resetPositions();
        }
    }

    private void handleGhostCollision(Ghost ghost) {
        if (ghost.scared) {
            // Eat the ghost
            score += 200 * ghostEatMultiplier;
            ghostEatMultiplier *= 2;
            ghost.reset();
            ghost.scared   = false;
            ghost.flashing = false;
            ghost.scaredTimer = 0;
            ghost.image = ghost.normalImage;
        } else {
            lives--;
            if(lives==0){

    gameOver=true;

    stats.stop();

    stats.print(USE_BFS ? "BFS" : "Random");

    updateHighScore();

    return;
}
            resetPositions();
        }
    }

    // ── BFS: find best direction for ghost to move toward target tile ─────────
    private char bfsDirection(Ghost ghost, int[] targetTile) {
        stats.recordBFSCall();
        int startCol = ghost.x / tileSize;
        int startRow = ghost.y / tileSize;
        int goalCol  = clamp(targetTile[0], 0, columnCount - 1);
        int goalRow  = clamp(targetTile[1], 0, rowCount    - 1);

        // Mark walls
        boolean[][] blocked = new boolean[rowCount][columnCount];
        for (Block w : walls) {
            int wr = w.y / tileSize, wc = w.x / tileSize;
            if (wr >= 0 && wr < rowCount && wc >= 0 && wc < columnCount)
                blocked[wr][wc] = true;
        }

        // BFS
        int[][] prev = new int[rowCount * columnCount][2];
        for (int[] p : prev) Arrays.fill(p, -1);
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startRow, startCol});
        boolean[][] visited = new boolean[rowCount][columnCount];
        visited[startRow][startCol] = true;

        int[][] deltas = {{-1,0},{1,0},{0,-1},{0,1}};
        char[]  dirMap = {'U','D','L','R'};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int r = cur[0], c = cur[1];
            if (r == goalRow && c == goalCol) break;
            for (int i = 0; i < 4; i++) {
                int nr = r + deltas[i][0];
                int nc = c + deltas[i][1];
                // Wrap columns for tunnel
                if (nc < 0) nc = columnCount - 1;
                else if (nc >= columnCount) nc = 0;
                if (nr < 0 || nr >= rowCount) continue;
                if (!visited[nr][nc] && !blocked[nr][nc]) {
                    visited[nr][nc] = true;
                    prev[nr * columnCount + nc] = new int[]{r, c};
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        // Trace back to find first step from start
        int r = goalRow, c = goalCol;
        if (prev[r * columnCount + c][0] == -1) return randomDir(ghost); // no path
        while (true) {
            int[] p = prev[r * columnCount + c];
            if (p[0] == startRow && p[1] == startCol) {
                // (r,c) is the first step
                for (int i = 0; i < 4; i++) {
                    if (startRow + deltas[i][0] == r && startCol + deltas[i][1] == c)
                        return dirMap[i];
                }
                break;
            }
            r = p[0]; c = p[1];
        }
        return randomDir(ghost);
    }

    private char randomDir(Ghost ghost) {
        char[] dirs = {'U','D','L','R'};
        // Prefer not reversing direction
        List<Character> options = new ArrayList<>();
        for (char d : dirs) options.add(d);
        Collections.shuffle(options);
        return options.get(0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public boolean collision(Block a, Block b) {
        return a.x < b.x + b.width  &&
               a.x + a.width > b.x  &&
               a.y < b.y + b.height &&
               a.y + a.height > b.y;
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public void resetPositions() {
        pacman.reset();
        pacman.velocityX = 0; pacman.velocityY = 0;
        for (Ghost ghost : ghosts) {
            ghost.reset();
            ghost.scared = false; ghost.flashing = false; ghost.scaredTimer = 0;
            ghost.image  = ghost.normalImage;
            char[] dirs = {'U','D','L','R'};
            ghost.updateDirection(dirs[new Random().nextInt(4)]);
        }
    }

    // ── High score persistence ────────────────────────────────────────────────
    private void loadHighScore() {
        try (BufferedReader br = new BufferedReader(new FileReader(HIGH_SCORE_FILE))) {
            highScore = Integer.parseInt(br.readLine().trim());
        } catch (Exception e) { highScore = 0; }
    }

    private void updateHighScore() {
        if (score > highScore) {
            highScore = score;
            try (PrintWriter pw = new PrintWriter(new FileWriter(HIGH_SCORE_FILE))) {
                pw.println(highScore);
            } catch (IOException e) { /* non-critical */ }
        }
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        move();
        repaint();
        if (gameOver) gameLoop.stop();
    }

    // ── Keyboard input ────────────────────────────────────────────────────────
    @Override public void keyTyped(KeyEvent e)   {}
    @Override public void keyPressed(KeyEvent e) {}

    @Override
    public void keyReleased(KeyEvent e) {
        if (gameOver) {
            loadMap(); resetPositions();
            lives = 3; score = 0; gameOver = false;
            gameLoop.start();
            return;
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP    -> pacman.updateDirection('U');
            case KeyEvent.VK_DOWN  -> pacman.updateDirection('D');
            case KeyEvent.VK_LEFT  -> pacman.updateDirection('L');
            case KeyEvent.VK_RIGHT -> pacman.updateDirection('R');
        }
        pacman.image = switch (pacman.direction) {
            case 'U' -> pacmanUpImage;
            case 'D' -> pacmanDownImage;
            case 'L' -> pacmanLeftImage;
            default  -> pacmanRightImage;
        };
    }
}