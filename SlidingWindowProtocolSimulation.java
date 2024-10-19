import java.util.*;

public class SlidingWindowProtocolSimulation {
    private static final Random random = new Random();
    private static final double LOSS_PROBABILITY = 0.3;

    public static void main(String[] args) {
        int windowSize = 3;
        int totalFrames = 5;

        System.out.println("Go-Back-N Simulation:");
        simulateProtocol(windowSize, totalFrames, true);

        System.out.println("\nSelective Repeat Simulation:");
        simulateProtocol(windowSize, totalFrames, false);
    }

    private static void simulateProtocol(int windowSize, int totalFrames, boolean isGoBackN) {
        int base = 0;
        Set<Integer> receivedFrames = new HashSet<>();

        while (base < totalFrames) {
            System.out.println("Current window: [" + base + ", " + Math.min(base + windowSize - 1, totalFrames - 1) + "]");

            for (int i = base; i < Math.min(base + windowSize, totalFrames); i++) {
                if (!receivedFrames.contains(i)) {
                    boolean frameLost = random.nextDouble() < LOSS_PROBABILITY;
                    System.out.println("Sending frame " + i + (frameLost ? " (lost)" : " (received)"));
                    if (!frameLost) {
                        receivedFrames.add(i);
                    }
                }
            }

            if (isGoBackN) {
                if (receivedFrames.contains(base)) {
                    while (receivedFrames.contains(base) && base < totalFrames) {
                        System.out.println("Acknowledging frame " + base);
                        base++;
                    }
                } else {
                    System.out.println("Timeout: Go-Back-N to frame " + base);
                }
            } else {
                for (int i = base; i < Math.min(base + windowSize, totalFrames); i++) {
                    if (receivedFrames.contains(i)) {
                        System.out.println("Acknowledging frame " + i);
                        if (i == base) {
                            while (receivedFrames.contains(base) && base < totalFrames) {
                                base++;
                            }
                        }
                    } else {
                        System.out.println("Timeout: Resend frame " + i);
                    }
                }
            }

            System.out.println("New base: " + base);
            System.out.println();
        }

        System.out.println("All frames transmitted successfully");
    }
}