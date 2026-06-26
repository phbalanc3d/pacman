import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;

public class Statistics {

    private long startTime;
    private long endTime;

    private double totalDistance = 0;
    private int samples = 0;

    private int wallHits = 0;
    private int directionChanges = 0;
    private int bfsCalls = 0;

    private static final String CSV_FILE = "pacman_stats.csv";

    public void start() {
        // Reset all per-game counters on start so each game is independent
        totalDistance  = 0;
        samples        = 0;
        wallHits       = 0;
        directionChanges = 0;
        bfsCalls       = 0;
        startTime = System.currentTimeMillis();
    }

    public void stop() {
        endTime = System.currentTimeMillis();
    }

    public void recordDistance(double distance) {
        totalDistance += distance;
        samples++;
    }

    public void recordWallHit()        { wallHits++; }
    public void recordDirectionChange(){ directionChanges++; }
    public void recordBFSCall()        { bfsCalls++; }

    // ── Getters (for in-game HUD display) ────────────────────────────────────
    public double getAvgDistance()  { return samples > 0 ? totalDistance / samples : 0; }
    public double getTimeSec()      { return (endTime - startTime) / 1000.0; }
    public int    getWallHits()     { return wallHits; }
    public int    getDirectionChanges() { return directionChanges; }
    public int    getBfsCalls()     { return bfsCalls; }

    // ── Console print (same as before) ───────────────────────────────────────
    public void print(String algorithm) {
        System.out.println("\n==============================");
        System.out.println("Algorithm : " + algorithm);
        System.out.printf("Time to Catch : %.2f s\n", getTimeSec());
        System.out.printf("Average Distance : %.2f px\n", getAvgDistance());
        System.out.println("Wall Hits : " + wallHits);
        System.out.println("Direction Changes : " + directionChanges);
        System.out.println("BFS Calls : " + bfsCalls);
        System.out.println("==============================");
    }

    // ── CSV export ────────────────────────────────────────────────────────────
    // Appends one row per game. Creates the file with a header if it doesn't exist.
    // You can open this in Excel / Google Sheets to plot BFS vs Random.
    public void saveToCSV(String algorithm) {
        boolean exists = new File(CSV_FILE).exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            if (!exists) {
                // Write header on first run
                pw.println("algorithm,time_sec,avg_distance_px,wall_hits,direction_changes,bfs_calls");
            }
            pw.printf("%s,%.2f,%.2f,%d,%d,%d%n",
                algorithm,
                getTimeSec(),
                getAvgDistance(),
                wallHits,
                directionChanges,
                bfsCalls);
        } catch (IOException e) {
            System.err.println("Could not write stats CSV: " + e.getMessage());
        }
    }
}