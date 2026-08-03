package com.deep.civpressure.ore;

public record OreRedistributionResult(Status status, int removed, int added) {
    public enum Status {
        PROCESSED,
        ALREADY_PROCESSED,
        UNSUPPORTED_WORLD,
        PERSISTENCE_FAILED
    }
}
