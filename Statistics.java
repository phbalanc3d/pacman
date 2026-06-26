public class Statistics {

    private long startTime;
    private long endTime;

    private double totalDistance = 0;
    private int samples = 0;

    private int wallHits = 0;
    private int directionChanges = 0;
    private int bfsCalls = 0;

public void recordBFSCall() {
    bfsCalls++;
}

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void stop() {
        endTime = System.currentTimeMillis();
    }

    public void recordDistance(double distance) {
        totalDistance += distance;
        samples++;
    }

    public void recordWallHit() {
        wallHits++;
    }

    public void recordDirectionChange() {
        directionChanges++;
    }

    public void print(String algorithm) {

        System.out.println("\n==============================");
        System.out.println("Algorithm : " + algorithm);
        System.out.printf("Time to Catch : %.2f s\n",
                (endTime-startTime)/1000.0);

        System.out.printf("Average Distance : %.2f px\n",
                totalDistance/samples);

        System.out.println("Wall Hits : " + wallHits);

        System.out.println("Direction Changes : " + directionChanges);
        System.out.println("BFS Calls : " + bfsCalls);

        System.out.println("==============================");
    }

}