package com.deep.civpressure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigValueParserTest {

    @Test
    void percentOnChancePathBecomesAFraction() {
        ConfigValueParser.Result result = ConfigValueParser.parse(
                "nightfall.giant.chance-per-pack", 0.25, "30%");
        assertTrue(result.ok());
        assertEquals(0.3, (Double) result.value(), 1.0e-9);
    }

    @Test
    void wholeNumberAboveOneOnChancePathIsTreatedAsPercent() {
        ConfigValueParser.Result result = ConfigValueParser.parse(
                "nightfall.giant.chance-per-pack", 0.15, "30");
        assertTrue(result.ok());
        assertEquals(0.3, (Double) result.value(), 1.0e-9);
    }

    @Test
    void fractionOnChancePathStaysAFraction() {
        ConfigValueParser.Result result = ConfigValueParser.parse(
                "nightfall.skeleton-traps.max-chance", 0.25, "0.3");
        assertTrue(result.ok());
        assertEquals(0.3, (Double) result.value(), 1.0e-9);
    }

    @Test
    void aliasesResolveToFullPaths() {
        assertEquals(
                "nightfall.giant.chance-per-pack",
                ConfigValueParser.resolvePath("giant-chance"));
        assertEquals(
                "nightfall.giant.chance-per-pack",
                ConfigValueParser.resolvePath("nightfall-giant-chance"));
        assertEquals(
                "nightfall.skeleton-traps.max-chance",
                ConfigValueParser.resolvePath("skeleton-trap-chance"));
        assertEquals("fall-damage.multiplier", ConfigValueParser.resolvePath("fall-damage.multiplier"));
    }

    @Test
    void booleanAndIntegerKeepTheirTypes() {
        ConfigValueParser.Result enabled = ConfigValueParser.parse(
                "modules.nightfall.enabled", true, "false");
        assertTrue(enabled.ok());
        assertEquals(Boolean.FALSE, enabled.value());

        ConfigValueParser.Result cap = ConfigValueParser.parse(
                "nightfall.cap-nights", 50, "40");
        assertTrue(cap.ok());
        assertEquals(40, cap.value());
        assertInstanceOf(Integer.class, cap.value());
    }

    @Test
    void listsCannotBeSetFromChat() {
        ConfigValueParser.Result result = ConfigValueParser.parse(
                "nightfall.sounds.list", java.util.List.of("ambient.cave"), "ambient.cave");
        assertFalse(result.ok());
    }

    @Test
    void unknownBooleanIsRejected() {
        ConfigValueParser.Result result = ConfigValueParser.parse(
                "modules.nightfall.enabled", true, "maybe");
        assertFalse(result.ok());
    }
}
