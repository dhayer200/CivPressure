package com.deep.civpressure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses chat input into a YAML-config value using the existing stored type.
 * Chance keys stored as 0–1 fractions also accept {@code 30%} or {@code 30}.
 */
public final class ConfigValueParser {
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("giant-chance", "nightfall.giant.chance-per-pack");
        ALIASES.put("nightfall-giant-chance", "nightfall.giant.chance-per-pack");
        ALIASES.put("siege-chance", "nightfall.spawns.siege-chance-max");
        ALIASES.put("siege-pack", "nightfall.spawns.max-pack-size");
        ALIASES.put("skeleton-trap-chance", "nightfall.skeleton-traps.max-chance");
        ALIASES.put("polar-bear-chance", "mountain-polar-bears.spawn-chance");
        ALIASES.put("fall-damage", "fall-damage.multiplier");
    }

    private ConfigValueParser() {
    }

    public static String resolvePath(String rawPath) {
        String key = rawPath.trim();
        String alias = ALIASES.get(key.toLowerCase(Locale.ROOT));
        return alias != null ? alias : key;
    }

    public static Map<String, String> aliases() {
        return Map.copyOf(ALIASES);
    }

    public static Result parse(String path, Object current, String rawValue) {
        if (current instanceof List<?>) {
            return Result.error("List values cannot be changed from chat. Edit config.yml for lists.");
        }
        if (current instanceof Map<?, ?> || current instanceof Iterable<?>) {
            return Result.error("That key is a section, not a single value.");
        }

        String trimmed = rawValue.trim();
        if (current instanceof Boolean) {
            if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("on")
                    || trimmed.equalsIgnoreCase("yes")) {
                return Result.ok(Boolean.TRUE);
            }
            if (trimmed.equalsIgnoreCase("false") || trimmed.equalsIgnoreCase("off")
                    || trimmed.equalsIgnoreCase("no")) {
                return Result.ok(Boolean.FALSE);
            }
            return Result.error("Expected true or false.");
        }
        if (current instanceof Integer || current instanceof Long) {
            try {
                long parsed = Long.parseLong(trimmed);
                if (current instanceof Integer && parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                    return Result.ok((int) parsed);
                }
                return Result.ok(parsed);
            } catch (NumberFormatException exception) {
                return Result.error("Expected a whole number.");
            }
        }
        if (current instanceof Number) {
            Double parsed = parseDouble(path, trimmed);
            if (parsed == null) {
                return Result.error("Expected a number, or a percent like 30%.");
            }
            return Result.ok(parsed);
        }
        return Result.ok(trimmed);
    }

    public static String format(Object value) {
        if (value instanceof Double number) {
            if (Math.abs(number - Math.rint(number)) < 1.0e-9) {
                return Long.toString(Math.round(number));
            }
            String text = Double.toString(number);
            if (looksLikeChance(number)) {
                return text + " (" + Math.round(number * 100.0) + "%)";
            }
            return text;
        }
        if (value instanceof List<?> list) {
            return list.toString();
        }
        return String.valueOf(value);
    }

    private static Double parseDouble(String path, String trimmed) {
        String body = trimmed;
        boolean percentSuffix = body.endsWith("%");
        if (percentSuffix) {
            body = body.substring(0, body.length() - 1).trim();
        }
        double parsed;
        try {
            parsed = Double.parseDouble(body);
        } catch (NumberFormatException exception) {
            return null;
        }
        boolean chancePath = path.toLowerCase(Locale.ROOT).contains("chance");
        if (percentSuffix || (chancePath && parsed > 1.0)) {
            parsed = parsed / 100.0;
        }
        return parsed;
    }

    private static boolean looksLikeChance(double number) {
        return number >= 0.0 && number <= 1.0;
    }

    public record Result(boolean ok, Object value, String error) {
        static Result ok(Object value) {
            return new Result(true, value, null);
        }

        static Result error(String message) {
            return new Result(false, null, message);
        }
    }
}
