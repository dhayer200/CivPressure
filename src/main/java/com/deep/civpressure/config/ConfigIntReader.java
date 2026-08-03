package com.deep.civpressure.config;

/**
 * Minimal seam over integer configuration lookups so rate logic can be unit
 * tested without a running server. {@link ConfigManager} is the production
 * implementation.
 */
@FunctionalInterface
public interface ConfigIntReader {
    int getInt(String path, int defaultValue);
}
