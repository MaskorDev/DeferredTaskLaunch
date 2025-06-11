package org.example;

public class RetryPolicyParam {
    private final boolean isExponential;
    private final int maxAttempts;
    private final double base;
    private final long maxDelayMs;

    public RetryPolicyParam(boolean isExponential,
                            int maxAttempts,
                            double base,
                            long maxDelayMs) {
        this.isExponential = isExponential;
        this.maxAttempts = maxAttempts;
        this.base = base;
        this.maxDelayMs = maxDelayMs;
    }

    public long calculateDelay(int attemptNumber) {

        double delay = Math.pow(base, attemptNumber) * 1000;

        return Math.min((long) delay, maxDelayMs);
    }

    public boolean isExponential() {
        return isExponential;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public double getBase() {
        return base;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

}
