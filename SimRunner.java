import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

/**
 * Headless Pac-Man simulation — no GUI, no Timer, no image loading.
 * Compares 3 ghost AI strategies: Random, BFS, A* (Manhattan heuristic).
 * Runs RUNS_PER_MODE games per algorithm. Pac-Man uses a flee heuristic.
 *
 * Compile:  javac SimRunner.java
 * Run:      java SimRunner
 * Output:   console + pacman_sim_stats.csv
 */
public class SimRunner {

    static final int RUNS_PER_MODE = 20;
    static final int MAX_TICKS     = 6000;
    static final int TILE          = 32;
    static final int ROWS          = 21;
    static final int COLS          = 19;
    static final String CSV        = "pacman_sim_stats.csv";

    // Algorithm modes
    static final int MODE_RANDOM = 0;
    static final int MODE_BFS    = 1;
    static final int MODE_ASTAR  = 2;

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
        final int mode;
        final boolean[][] walls = new boolean[ROWS][COLS];
        final List<E> ghosts = new ArrayList<>();
        E pac;
        int ticks, wallHits, pathCalls, nodesExpanded;
        long samples; double avgDist;
        boolean caught;
        final Random rng = new Random();

        Game(int mode) {
            this.mode = mode;
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
            // Pac-Man flee heuristic
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
                    g.face(nextDir(g));
                    gx = g.x + g.vx; gy = g.y + g.vy;
                }
                if (!wall(gx, gy)) { g.x = gx; g.y = gy; }
                tunnel(g);
                if (g.x % TILE == 0 && g.y % TILE == 0) g.face(nextDir(g));
                if (hits(pac, g)) caught = true;
            }

            // Sample avg ghost distance (Welford online mean)
            for (E g : ghosts) {
                double d = Math.hypot(g.x - pac.x, g.y - pac.y);
                samples++;
                avgDist += (d - avgDist) / samples;
            }
        }

        // Dispatch to the right algorithm
        char nextDir(E g) {
            return switch (mode) {
                case MODE_BFS    -> bfsDir(g);
                case MODE_ASTAR  -> astarDir(g);
                default          -> rndDir();
            };
        }

        // ── BFS — O(V+E), guarantees shortest path, blind expansion ──────────
        // Each queue entry: {row, col, firstDirIndex}
        char bfsDir(E g) {
            pathCalls++;
            int sc = clamp(g.x/TILE,0,COLS-1), sr = clamp(g.y/TILE,0,ROWS-1);
            int[] t = target(g);
            int gc = clamp(t[0],0,COLS-1), gr = clamp(t[1],0,ROWS-1);
            if (sr==gr && sc==gc) return g.dir;

            int[][] D = {{-1,0},{1,0},{0,-1},{0,1}};
            char[] M = {'U','D','L','R'};
            boolean[][] vis = new boolean[ROWS][COLS];
            vis[sr][sc] = true;
            Queue<int[]> q = new LinkedList<>();
            for (int i=0;i<4;i++) {
                int nr=sr+D[i][0], nc=sc+D[i][1];
                if (nc<0) nc=COLS-1; else if(nc>=COLS) nc=0;
                if (nr<0||nr>=ROWS||walls[nr][nc]||vis[nr][nc]) continue;
                vis[nr][nc]=true; q.add(new int[]{nr,nc,i});
                nodesExpanded++;
            }
            while (!q.isEmpty()) {
                int[] cur=q.poll();
                if (cur[0]==gr&&cur[1]==gc) return M[cur[2]];
                for (int i=0;i<4;i++) {
                    int nr=cur[0]+D[i][0], nc=cur[1]+D[i][1];
                    if (nc<0) nc=COLS-1; else if(nc>=COLS) nc=0;
                    if (nr<0||nr>=ROWS||walls[nr][nc]||vis[nr][nc]) continue;
                    vis[nr][nc]=true; q.add(new int[]{nr,nc,cur[2]});
                    nodesExpanded++;
                }
            }
            return rndDir();
        }

        // ── A* — f(n) = g(n) + h(n), h = Manhattan distance ──────────────────
        // Expands fewer nodes than BFS by prioritising cells closer to the goal.
        // Each PQ entry: {f, g_cost, row, col, firstDirIndex}
        char astarDir(E g) {
            pathCalls++;
            int sc = clamp(g.x/TILE,0,COLS-1), sr = clamp(g.y/TILE,0,ROWS-1);
            int[] t = target(g);
            int gc = clamp(t[0],0,COLS-1), gr = clamp(t[1],0,ROWS-1);
            if (sr==gr && sc==gc) return g.dir;

            int[][] D = {{-1,0},{1,0},{0,-1},{0,1}};
            char[] M = {'U','D','L','R'};

            // gCost[r][c] = best known cost from start to (r,c)
            int[][] gCost = new int[ROWS][COLS];
            for (int[] row : gCost) java.util.Arrays.fill(row, Integer.MAX_VALUE);
            gCost[sr][sc] = 0;

            // PQ sorted by f = gCost + Manhattan heuristic
            PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
            // Seed with neighbours of start (carry firstDirIndex like BFS)
            for (int i=0;i<4;i++) {
                int nr=sr+D[i][0], nc=sc+D[i][1];
                if (nc<0) nc=COLS-1; else if(nc>=COLS) nc=0;
                if (nr<0||nr>=ROWS||walls[nr][nc]) continue;
                int h = Math.abs(nr-gr)+Math.abs(nc-gc);
                gCost[nr][nc] = 1;
                pq.add(new int[]{1+h, 1, nr, nc, i});
                nodesExpanded++;
            }

            while (!pq.isEmpty()) {
                int[] cur = pq.poll();
                int f=cur[0], cost=cur[1], r=cur[2], c=cur[3], firstIdx=cur[4];
                if (r==gr && c==gc) return M[firstIdx];
                if (cost > gCost[r][c]) continue; // stale entry
                for (int i=0;i<4;i++) {
                    int nr=r+D[i][0], nc2=c+D[i][1];
                    if (nc2<0) nc2=COLS-1; else if(nc2>=COLS) nc2=0;
                    if (nr<0||nr>=ROWS||walls[nr][nc2]) continue;
                    int ng = cost+1;
                    if (ng < gCost[nr][nc2]) {
                        gCost[nr][nc2] = ng;
                        int h = Math.abs(nr-gr)+Math.abs(nc2-gc);
                        pq.add(new int[]{ng+h, ng, nr, nc2, firstIdx});
                        nodesExpanded++;
                    }
                }
            }
            return rndDir();
        }

        // ── Shared helpers ────────────────────────────────────────────────────
        int[] target(E g) {
            int px=pac.x/TILE, py=pac.y/TILE;
            return switch (g.role) {
                case "blinky" -> new int[]{px, py};
                case "pinky"  -> {
                    int tx=px, ty=py;
                    switch(pac.dir){case 'U'->ty-=4;case 'D'->ty+=4;case 'L'->tx-=4;case 'R'->tx+=4;}
                    yield new int[]{tx, ty};
                }
                case "inky"  -> new int[]{COLS-1-px, ROWS-1-py};
                case "clyde" -> Math.hypot(g.x/TILE-px, g.y/TILE-py)<8
                    ? new int[]{1,ROWS-2} : new int[]{px,py};
                default -> new int[]{px,py};
            };
        }

        char fleDir() {
            E near = ghosts.get(0); double minD = Double.MAX_VALUE;
            for (E gh : ghosts) { double d=Math.hypot(gh.x-pac.x,gh.y-pac.y); if(d<minD){minD=d;near=gh;} }
            char best=pac.dir; double bestD=-1;
            int[][] off={{0,-TILE},{0,TILE},{-TILE,0},{TILE,0}};
            char[] dirs={'U','D','L','R'};
            for (int i=0;i<4;i++) {
                if (wall(pac.x+off[i][0],pac.y+off[i][1])) continue;
                double dist=Math.hypot(pac.x+off[i][0]-near.x,pac.y+off[i][1]-near.y);
                if (dist>bestD){bestD=dist;best=dirs[i];}
            }
            return best;
        }

        char anyOpen(int x, int y) {
            int[][] off={{0,-TILE},{0,TILE},{-TILE,0},{TILE,0}};
            char[] dirs={'U','D','L','R'};
            List<Integer> idx=new ArrayList<>(List.of(0,1,2,3));
            Collections.shuffle(idx,rng);
            for (int i:idx) if(!wall(x+off[i][0],y+off[i][1])) return dirs[i];
            return 'R';
        }

        boolean wall(int x, int y) {
            int r1=clamp(y/TILE,0,ROWS-1),c1=clamp(x/TILE,0,COLS-1);
            int r2=clamp((y+TILE-1)/TILE,0,ROWS-1),c2=clamp((x+TILE-1)/TILE,0,COLS-1);
            for (int r=r1;r<=r2;r++) for (int c=c1;c<=c2;c++) if(walls[r][c]) return true;
            return false;
        }

        boolean hits(E a, E b) {
            return a.x<b.x+TILE&&a.x+TILE>b.x&&a.y<b.y+TILE&&a.y+TILE>b.y;
        }

        void tunnel(E e) {
            if(e.x+TILE<0) e.x=COLS*TILE; else if(e.x>COLS*TILE) e.x=-TILE;
        }

        char rndDir() { return new char[]{'U','D','L','R'}[rng.nextInt(4)]; }
        int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    }

    // ── Main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws IOException {
        System.out.printf("=== Pac-Man AI Benchmark — %d runs × 3 algorithms ===%n%n", RUNS_PER_MODE);

        boolean exists = new File(CSV).exists();
        PrintWriter csv = new PrintWriter(new FileWriter(CSV, true));
        if (!exists)
            csv.println("algorithm,ticks,time_sec_equiv,avg_dist_px,wall_hits,path_calls,nodes_expanded,caught");

        String[] labels = {"Random", "BFS", "A*"};
        int[] modes     = {MODE_RANDOM, MODE_BFS, MODE_ASTAR};

        for (int m = 0; m < 3; m++) {
            String label = labels[m];
            System.out.println("──── " + label + " ────");

            int totTicks=0,totWalls=0,totCalls=0,totNodes=0,caughtCount=0;
            double totDist=0;

            for (int run=0; run<RUNS_PER_MODE; run++) {
                Game g = new Game(modes[m]);
                g.run();
                double sec = g.ticks/20.0;

                System.out.printf(
                    "  [%2d] ticks:%5d  time:%6.1fs  dist:%6.1fpx  walls:%4d  calls:%4d  nodes:%5d  caught:%b%n",
                    run+1, g.ticks, sec, g.avgDist,
                    g.wallHits, g.pathCalls, g.nodesExpanded, g.caught);
                csv.printf("%s,%d,%.2f,%.2f,%d,%d,%d,%b%n",
                    label,g.ticks,sec,g.avgDist,
                    g.wallHits,g.pathCalls,g.nodesExpanded,g.caught);

                totTicks+=g.ticks; totDist+=g.avgDist;
                totWalls+=g.wallHits; totCalls+=g.pathCalls;
                totNodes+=g.nodesExpanded;
                if(g.caught) caughtCount++;
            }

            System.out.printf(
                "%n  SUMMARY  time:%.1fs  dist:%.1fpx  walls:%.0f  calls:%.0f  nodes:%.0f  catchRate:%d/%d%n%n",
                totTicks/20.0/RUNS_PER_MODE, totDist/RUNS_PER_MODE,
                totWalls/(double)RUNS_PER_MODE, totCalls/(double)RUNS_PER_MODE,
                totNodes/(double)RUNS_PER_MODE, caughtCount, RUNS_PER_MODE);
        }

        csv.close();
        System.out.println("Saved → " + CSV);
        System.out.println("\nKey metric to compare: 'nodes' — A* should expand fewer than BFS with same catch rate.");
    }
}