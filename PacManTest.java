import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Pac-Man ghost AI algorithms.
 * Tests BFS and A* on controlled grids — independent of GUI, images, or Timer.
 *
 * Run with Maven:  mvn test
 * Run with Gradle: gradle test
 * Run manually:
 *   Download JUnit Platform Console Standalone jar from:
 *   https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.1/
 *   Then:
 *   javac -cp junit-platform-console-standalone-1.10.1.jar PacManTest.java SimRunner.java
 *   java  -cp .:junit-platform-console-standalone-1.10.1.jar \
 *         org.junit.platform.console.ConsoleLauncher --select-class=PacManTest
 */
public class PacManTest {

    // ── Helpers: build a controlled Game with a custom wall grid ─────────────
    // We subclass SimRunner.Game to inject a custom wall layout for precise testing.
    static SimRunner.Game gameWithWalls(boolean[][] walls, int mode) {
        SimRunner.Game g = new SimRunner.Game(mode) {
            @Override boolean wall(int x, int y) {
                int r = clamp(y/TILE,0,ROWS-1), c = clamp(x/TILE,0,COLS-1);
                return walls[r][c];
            }
        };
        // Override wall array directly
        for (int r=0; r<SimRunner.ROWS; r++)
            for (int c=0; c<SimRunner.COLS; c++)
                g.walls[r][c] = walls[r][c];
        return g;
    }

    // Build a minimal all-open wall grid (only boundary walls)
    static boolean[][] openGrid() {
        boolean[][] w = new boolean[SimRunner.ROWS][SimRunner.COLS];
        for (int r=0; r<SimRunner.ROWS; r++) {
            w[r][0] = true; w[r][SimRunner.COLS-1] = true;
        }
        for (int c=0; c<SimRunner.COLS; c++) {
            w[0][c] = true; w[SimRunner.ROWS-1][c] = true;
        }
        return w;
    }

    // ── BFS correctness tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("BFS: ghost already at goal returns current direction")
    void bfs_alreadyAtGoal_returnsCurrentDir() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        SimRunner.E ghost = new SimRunner.E(5*32, 5*32, "blinky");
        ghost.face('R');
        g.pac = new SimRunner.E(5*32, 5*32, "pacman");
        // ghost tile == pac tile → should not crash, returns current dir
        char result = g.bfsDir(ghost);
        assertEquals('R', result, "should keep current direction when already at goal");
    }

    @Test
    @DisplayName("BFS: routes correctly in open corridor — moves right toward target")
    void bfs_openCorridor_movesRight() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        // Ghost at col 3, pac at col 8, same row — direct path right
        g.pac = new SimRunner.E(8*32, 10*32, "pacman");
        SimRunner.E ghost = new SimRunner.E(3*32, 10*32, "blinky");
        ghost.face('U');
        char dir = g.bfsDir(ghost);
        assertEquals('R', dir, "should move right toward Pac-Man in open corridor");
    }

    @Test
    @DisplayName("BFS: routes correctly upward when pac is above ghost")
    void bfs_openCorridor_movesUp() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.pac = new SimRunner.E(9*32, 3*32, "pacman");
        SimRunner.E ghost = new SimRunner.E(9*32, 15*32, "blinky");
        ghost.face('R');
        char dir = g.bfsDir(ghost);
        assertEquals('U', dir, "should move up toward Pac-Man when directly above");
    }

    @Test
    @DisplayName("BFS: increments pathCalls counter each invocation")
    void bfs_incrementsPathCallsCounter() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.pac = new SimRunner.E(9*32, 3*32, "pacman");
        SimRunner.E ghost = new SimRunner.E(9*32, 15*32, "blinky");
        ghost.face('R');
        int before = g.pathCalls;
        g.bfsDir(ghost);
        assertEquals(before + 1, g.pathCalls, "pathCalls should increment by 1 per BFS call");
    }

    @Test
    @DisplayName("BFS: expands at least 1 node per call (nodesExpanded tracked)")
    void bfs_expandsNodes() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.pac = new SimRunner.E(15*32, 10*32, "pacman");
        SimRunner.E ghost = new SimRunner.E(3*32, 10*32, "blinky");
        ghost.face('U');
        g.bfsDir(ghost);
        assertTrue(g.nodesExpanded > 0, "BFS should expand at least 1 node");
    }

    // ── A* correctness tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("A*: produces same direction as BFS in open corridor")
    void astar_matchesBFS_openCorridor() {
        SimRunner.Game bfsGame   = new SimRunner.Game(SimRunner.MODE_BFS);
        SimRunner.Game astarGame = new SimRunner.Game(SimRunner.MODE_ASTAR);

        bfsGame.pac   = new SimRunner.E(15*32, 10*32, "pacman");
        astarGame.pac = new SimRunner.E(15*32, 10*32, "pacman");

        SimRunner.E bfsGhost   = new SimRunner.E(3*32, 10*32, "blinky");
        SimRunner.E astarGhost = new SimRunner.E(3*32, 10*32, "blinky");
        bfsGhost.face('U'); astarGhost.face('U');

        char bfsDir   = bfsGame.bfsDir(bfsGhost);
        char astarDir = astarGame.astarDir(astarGhost);
        assertEquals(bfsDir, astarDir,
            "A* should find the same optimal direction as BFS on an open path");
    }

    @Test
    @DisplayName("A*: expands fewer or equal nodes than BFS on same path")
    void astar_expandsFewerNodesThanBFS() {
        SimRunner.Game bfsGame   = new SimRunner.Game(SimRunner.MODE_BFS);
        SimRunner.Game astarGame = new SimRunner.Game(SimRunner.MODE_ASTAR);

        bfsGame.pac   = new SimRunner.E(15*32, 5*32, "pacman");
        astarGame.pac = new SimRunner.E(15*32, 5*32, "pacman");

        SimRunner.E bfsGhost   = new SimRunner.E(2*32, 17*32, "blinky");
        SimRunner.E astarGhost = new SimRunner.E(2*32, 17*32, "blinky");
        bfsGhost.face('U'); astarGhost.face('U');

        bfsGame.bfsDir(bfsGhost);
        astarGame.astarDir(astarGhost);

        assertTrue(astarGame.nodesExpanded <= bfsGame.nodesExpanded,
            "A* should expand <= nodes vs BFS (heuristic prunes the search)." +
            " BFS=" + bfsGame.nodesExpanded + " A*=" + astarGame.nodesExpanded);
    }

    @Test
    @DisplayName("A*: increments pathCalls counter each invocation")
    void astar_incrementsPathCalls() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_ASTAR);
        g.pac = new SimRunner.E(9*32, 3*32, "pacman");
        SimRunner.E ghost = new SimRunner.E(9*32, 15*32, "blinky");
        ghost.face('R');
        int before = g.pathCalls;
        g.astarDir(ghost);
        assertEquals(before + 1, g.pathCalls);
    }

    // ── Ghost personality / targeting tests ───────────────────────────────────

    @Test
    @DisplayName("Blinky target: equals Pac-Man's exact tile")
    void blinky_targetsExactTile() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.pac = new SimRunner.E(10*32, 7*32, "pacman");
        SimRunner.E blinky = new SimRunner.E(3*32, 3*32, "blinky");
        int[] t = g.target(blinky);
        assertEquals(10, t[0], "Blinky target col should match Pac-Man col");
        assertEquals(7,  t[1], "Blinky target row should match Pac-Man row");
    }

    @Test
    @DisplayName("Pinky target: 4 tiles ahead of Pac-Man's direction")
    void pinky_targetsFourTilesAhead() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.pac = new SimRunner.E(10*32, 10*32, "pacman");
        g.pac.face('R'); // moving right
        SimRunner.E pinky = new SimRunner.E(3*32, 3*32, "pinky");
        int[] t = g.target(pinky);
        assertEquals(14, t[0], "Pinky should aim 4 tiles ahead (right): col 10+4=14");
        assertEquals(10, t[1], "Pinky row should stay same when moving right");
    }

    @Test
    @DisplayName("Inky target: opposite corner from Pac-Man")
    void inky_targetsOppositeCorner() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.pac = new SimRunner.E(3*32, 3*32, "pacman"); // col=3, row=3
        SimRunner.E inky = new SimRunner.E(10*32, 10*32, "inky");
        int[] t = g.target(inky);
        assertEquals(SimRunner.COLS-1-3, t[0], "Inky col should be COLS-1-pacCol");
        assertEquals(SimRunner.ROWS-1-3, t[1], "Inky row should be ROWS-1-pacRow");
    }

    @Test
    @DisplayName("Clyde target: chases when far, scatters when close")
    void clyde_scattersWhenClose() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        // Place Pac-Man very close to Clyde (distance < 8 tiles)
        g.pac = new SimRunner.E(5*32, 10*32, "pacman");
        SimRunner.E clyde = new SimRunner.E(6*32, 10*32, "clyde"); // 1 tile away
        int[] t = g.target(clyde);
        // Should scatter to bottom-left corner
        assertEquals(1,                t[0], "Clyde should scatter to col 1 when close");
        assertEquals(SimRunner.ROWS-2, t[1], "Clyde should scatter to bottom row when close");
    }

    // ── Collision detection tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Collision: overlapping entities detected")
    void collision_overlapping() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_RANDOM);
        SimRunner.E a = new SimRunner.E(100, 100, "pacman");
        SimRunner.E b = new SimRunner.E(110, 100, "blinky"); // overlaps
        assertTrue(g.hits(a, b), "Overlapping entities should collide");
    }

    @Test
    @DisplayName("Collision: non-overlapping entities not detected")
    void collision_noOverlap() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_RANDOM);
        SimRunner.E a = new SimRunner.E(100, 100, "pacman");
        SimRunner.E b = new SimRunner.E(200, 100, "blinky"); // gap > TILE
        assertFalse(g.hits(a, b), "Entities with gap > TILE should not collide");
    }

    // ── Simulation correctness tests ──────────────────────────────────────────

    @Test
    @DisplayName("BFS simulation: catches Pac-Man within MAX_TICKS")
    void bfsSimulation_catchesPacMan() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.run();
        assertTrue(g.caught, "BFS ghosts should always catch Pac-Man");
        assertTrue(g.ticks <= SimRunner.MAX_TICKS, "should finish within tick limit");
    }

    @Test
    @DisplayName("A* simulation: catches Pac-Man within MAX_TICKS")
    void astarSimulation_catchesPacMan() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_ASTAR);
        g.run();
        assertTrue(g.caught, "A* ghosts should always catch Pac-Man");
        assertTrue(g.ticks <= SimRunner.MAX_TICKS, "should finish within tick limit");
    }

    @Test
    @DisplayName("Statistics: avgDist is positive after a full simulation run")
    void stats_avgDistIsPositive() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_BFS);
        g.run();
        assertTrue(g.avgDist > 0, "average distance must be positive after running");
    }

    @Test
    @DisplayName("Statistics: nodesExpanded is 0 for Random mode")
    void stats_randomExpandsNoNodes() {
        SimRunner.Game g = new SimRunner.Game(SimRunner.MODE_RANDOM);
        g.run();
        assertEquals(0, g.nodesExpanded,
            "Random mode never calls BFS/A*, so nodesExpanded must stay 0");
    }
}