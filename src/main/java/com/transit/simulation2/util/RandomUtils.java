package com.transit.simulation2.util;

import java.util.Random;

/**
 * The sampling routines the model needs beyond the uniform and Gaussian draws that
 * {@link Random} provides natively: exponential, gamma, beta and Poisson variates, bounded
 * uniform draws, and sampling without replacement. Every routine draws from the generator it is
 * given, so a run is reproducible from its seed.
 */
public final class RandomUtils {

    private RandomUtils() {
    }

    public static double nextExponential(Random rng, double mean) {
        double u = rng.nextDouble();
        while (u <= 0.0) {
            u = rng.nextDouble();
        }
        return -mean * Math.log(u);
    }

    public static double nextGamma(Random rng, double shape, double scale) {
        if (shape < 1.0) {
            double u = rng.nextDouble();
            return nextGamma(rng, 1.0 + shape, scale) * Math.pow(u, 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = rng.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0);
            v = v * v * v;
            double u = rng.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x) {
                return d * v * scale;
            }
            if (Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }

    public static double nextBeta(Random rng, double alpha, double beta) {
        double x = nextGamma(rng, alpha, 1.0);
        double y = nextGamma(rng, beta, 1.0);
        return x / (x + y);
    }

    public static int nextPoisson(Random rng, double lambda) {
        if (lambda <= 0) return 0;
        double l = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    public static double uniform(Random rng, double lo, double hi) {
        return lo + rng.nextDouble() * (hi - lo);
    }

    public static int uniformInt(Random rng, int lo, int hi) {
        return lo + rng.nextInt(hi - lo);
    }

    /** Sample k distinct indices from [0, populationSize) without replacement. */
    public static int[] sampleWithoutReplacement(Random rng, int populationSize, int k) {
        if (k <= 0 || populationSize <= 0) return new int[0];
        int n = Math.min(k, populationSize);
        Integer[] pool = new Integer[populationSize];
        for (int i = 0; i < populationSize; i++) pool[i] = i;
        java.util.Collections.shuffle(java.util.Arrays.asList(pool), rng);
        int[] result = new int[n];
        for (int i = 0; i < n; i++) result[i] = pool[i];
        return result;
    }
}
