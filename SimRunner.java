import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Headless Pac-Man simulation — no GUI, no Timer, no image loading.
 * Runs RUNS_PER_MODE games per algorithm at full CPU speed.
 * Pac-Man is controlled by a flee-the-nearest-ghost heuristic.
 * Results printed to console and saved to pacman_sim_stats.csv.
 *
 * Compile:  javac SimRunner.java
 * Run:      java SimRunner
 */
public class SimRunner {

    static final int RUNS_PER_MODE = 20;
    static final int MAX_TICKS     = 6000;
    static final int TILE           = 32;
    static final int ROWS           = 21;
    static final int COLS           = 19;
    static final String CSV         = "pacman_sim_stats.csv";

    static final String[] MAP = {
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

    // ── Entity ────────────────────────────────────────────────────────────────
    static class E {
        int x, y, sx, sy, vx, vy;
        char dir = 'R';
        String role;

        E(int x, int y, String role) {
            this.x = sx = x; this.y = sy = y; this.role = role;
        }

        int spd() { return role.equals("pacman") ? 8 : 4; }

        void face(char d) {
            dir = d; vx = vy = 0; int s = spd();
            switch (d) {
                case 'U' -> vy = -s; case 'D' -> vy =  s;
                case 'L' -> vx = -s; case 'R' -> vx =  s;
            }
        }
    }

    // ── Game ──────────────────────────────────────────────────────────────────
    static class Game {
        final boolean bfs;
        final boolean[][] walls = new boolean[ROWS][COLS];
        final List<E> ghosts = new ArrayList<>();
        E pac;
        int ticks, wallHits, bfsCalls;
        long samples; double avgDist;
        boolean caught;
        final Random rng = new Random();

        Game(boolean bfs) {
            this.bfs = bfs;
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    char ch = MAP[r].charAt(c);
                    walls[r][c] = ch == 'X';
                    if (ch == 'P') pac = new E(c*TILE, r*TILE, "pacman");
                    if (ch == 'b') ghosts.add(new E(c*TILE, r*TILE, "inky"));
                    if (ch == 'o') ghosts.add(new E(c*TILE, r*TILE, "clyde"));
                    if (ch == 'p') ghosts.add(new E(c*TILE, r*TILE, "pinky"));
                    if (ch == 'r') ghosts.add(new E(c*TILE, r*TILE, "blinky"));
                }
            }
            pac.face('R');
            for (E g : ghosts) g.face(rndDir());
        }

        void run() {
            while (ticks < MAX_TICKS && !caught) { step(); ticks++; }
        }

        void step() {
            // Pac-Man: flee nearest ghost at each tile boundary
            if (pac.x % TILE == 0 && pac.y % TILE == 0) pac.face(fleDir());
            int nx = pac.x + pac.vx, ny = pac.y + pac.vy;
            if (wall(nx, ny)) {
                wallHits++;
                pac.face(anyOpen(pac.x, pac.y));
                nx = pac.x + pac.vx; ny = pac.y + pac.vy;
            }
            if (!wall(nx, ny)) { pac.x = nx; pac.y = ny; }
            tunnel(pac);

            // Ghosts
            for (E g : ghosts) {
                int gx = g.x + g.vx, gy = g.y + g.vy;
                if (wall(gx, gy)) {
                    wallHits++;
                    g.face(bfs ? bfsDir(g) : rndDir());
                    gx = g.x + g.vx; gy = g.y + g.vy;
                }
                if (!wall(gx, gy)) { g.x = gx; g.y = gy; }
                tunnel(g);
                if (g.x % TILE == 0 && g.y % TILE == 0) g.face(bfs ? bfsDir(g) : rndDir());
                if (hits(pac, g)) caught = true;
            }

            // Sample avg distance (Welford online mean)
            for (E g : ghosts) {
                double d = Math.hypot(g.x - pac.x, g.y - pac.y);
                samples++;
                avgDist += (d - avgDist) / samples;
            }
        }

        // Flee: pick direction maximising distance from nearest ghost
        char fleDir() {
            E near = ghosts.get(0);
            double minD = Double.MAX_VALUE;
            for (E g : ghosts) {
                double d = Math.hypot(g.x - pac.x, g.y - pac.y);
                if (d < minD) { minD = d; near = g; }
            }
            char best = pac.dir; double bestD = -1;
            int[][] offsets = {{0,-TILE},{0,TILE},{-TILE,0},{TILE,0}};
            char[] dirs = {'U','D','L','R'};
            for (int i = 0; i < 4; i++) {
                if (wall(pac.x + offsets[i][0], pac.y + offsets[i][1])) continue;
                double dist = Math.hypot(pac.x+offsets[i][0]-near.x, pac.y+offsets[i][1]-near.y);
                if (dist > bestD) { bestD = dist; best = dirs[i]; }
            }
            return best;
        }

        char anyOpen(int x, int y) {
            int[][] offsets = {{0,-TILE},{0,TILE},{-TILE,0},{TILE,0}};
            char[] dirs = {'U','D','L','R'};
            List<Integer> idx = new ArrayList<>(List.of(0,1,2,3));
            Collections.shuffle(idx, rng);
            for (int i : idx)
                if (!wall(x + offsets[i][0], y + offsets[i][1])) return dirs[i];
            return 'R';
        }

        // ── BFS (firstDir propagation — no traceback, no index bugs) ─────────
        char bfsDir(E g) {
            bfsCalls++;
            int sc = clamp(g.x/TILE, 0, COLS-1), sr = clamp(g.y/TILE, 0, ROWS-1);
            int[] t = target(g);
            int gc = clamp(t[0], 0, COLS-1), gr = clamp(t[1], 0, ROWS-1);
            if (sr == gr && sc == gc) return g.dir;

            int[][] D = {{-1,0},{1,0},{0,-1},{0,1}};
            char[] M = {'U','D','L','R'};
            boolean[][] vis = new boolean[ROWS][COLS];
            vis[sr][sc] = true;
            Queue<int[]> q = new LinkedList<>();
            for (int i = 0; i < 4; i++) {
                int nr = sr+D[i][0], nc = sc+D[i][1];
                if (nc < 0) nc = COLS-1; else if (nc >= COLS) nc = 0;
                if (nr < 0 || nr >= ROWS || walls[nr][nc] || vis[nr][nc]) continue;
                vis[nr][nc] = true;
                q.add(new int[]{nr, nc, i});
            }
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                if (cur[0] == gr && cur[1] == gc) return M[cur[2]];
                for (int i = 0; i < 4; i++) {
                    int nr = cur[0]+D[i][0], nc = cur[1]+D[i][1];
                    if (nc < 0) nc = COLS-1; else if (nc >= COLS) nc = 0;
                    if (nr < 0 || nr >= ROWS || walls[nr][nc] || vis[nr][nc]) continue;
                    vis[nr][nc] = true;
                    q.add(new int[]{nr, nc, cur[2]});
                }
            }
            return rndDir();
        }

        int[] target(E g) {
            int px = pac.x/TILE, py = pac.y/TILE;
            return switch (g.role) {
                case "blinky" -> new int[]{px, py};
                case "pinky"  -> {
                    int tx=px, ty=py;
                    switch (pac.dir) {
                        case 'U' -> ty -= 4; case 'D' -> ty += 4;
                        case 'L' -> tx -= 4; case 'R' -> tx += 4;
                    }
                    yield new int[]{tx, ty};
                }
                case "inky"  -> new int[]{COLS-1-px, ROWS-1-py};
                case "clyde" -> Math.hypot(g.x/TILE-px, g.y/TILE-py) < 8
                    ? new int[]{1, ROWS-2} : new int[]{px, py};
                default -> new int[]{px, py};
            };
        }

        boolean wall(int x, int y) {
            int r1=clamp(y/TILE,0,ROWS-1), c1=clamp(x/TILE,0,COLS-1);
            int r2=clamp((y+TILE-1)/TILE,0,ROWS-1), c2=clamp((x+TILE-1)/TILE,0,COLS-1);
            for (int r=r1;r<=r2;r++) for (int c=c1;c<=c2;c++) if (walls[r][c]) return true;
            return false;
        }

        boolean hits(E a, E b) {
            return a.x<b.x+TILE && a.x+TILE>b.x && a.y<b.y+TILE && a.y+TILE>b.y;
        }

        void tunnel(E e) {
            if (e.x+TILE < 0) e.x = COLS*TILE;
            else if (e.x > COLS*TILE) e.x = -TILE;
        }

        char rndDir() { return new char[]{'U','D','L','R'}[rng.nextInt(4)]; }
        int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    }

    // ── Main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        System.out.printf("=== Pac-Man AI Benchmark — %d runs per mode ===%n%n", RUNS_PER_MODE);

        boolean exists = new File(CSV).exists();
        PrintWriter csv = new PrintWriter(new FileWriter(CSV, true));
        if (!exists)
            csv.println("algorithm,ticks,time_sec_equiv,avg_dist_px,wall_hits,bfs_calls,caught");

        for (int mode = 0; mode < 2; mode++) {
            boolean useBFS = mode == 0;
            String label = useBFS ? "BFS" : "Random";
            System.out.println("──── " + label + " ────");

            int totTicks=0, totWalls=0, totBFS=0, caughtCount=0;
            double totDist = 0;

            for (int run = 0; run < RUNS_PER_MODE; run++) {
                Game g = new Game(useBFS);
                g.run();
                double sec = g.ticks / 20.0;

                System.out.printf("  [%2d] ticks:%5d  time:%6.1fs  dist:%6.1fpx  walls:%3d  bfsCalls:%4d  caught:%b%n",
                    run+1, g.ticks, sec, g.avgDist, g.wallHits, g.bfsCalls, g.caught);
                csv.printf("%s,%d,%.2f,%.2f,%d,%d,%b%n",
                    label, g.ticks, sec, g.avgDist, g.wallHits, g.bfsCalls, g.caught);

                totTicks += g.ticks; totDist += g.avgDist;
                totWalls += g.wallHits; totBFS += g.bfsCalls;
                if (g.caught) caughtCount++;
            }

            System.out.printf("%n  SUMMARY  avgTime:%.1fs  avgDist:%.1fpx  avgWalls:%.1f  catchRate:%d/%d%n%n",
                totTicks/20.0/RUNS_PER_MODE, totDist/RUNS_PER_MODE,
                totWalls/(double)RUNS_PER_MODE, caughtCount, RUNS_PER_MODE);
        }

        csv.close();
        System.out.println("Results saved to " + CSV);
    }
}