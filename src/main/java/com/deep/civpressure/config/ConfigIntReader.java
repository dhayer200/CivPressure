package com.deep.civpressure.config;

/**
 * Minimal seam over integer configuration lookups so rate logic can be unit
 * tested without a running server. {@link ConfigManager} is the production
 * implementation.
 */
@FunctionalInterface
public interface ConfigIntReader {
    int getInt(String path, int defaultValue);

    /**
     * Reads a string setting. Defaulted so test doubles can keep implementing
     * this interface as a simple {@code getInt} lambda; {@link ConfigManager}
     * overrides it with the real lookup.
     */
    default String getString(String path, String defaultValue) {
        return defaultValue;
    }
}
