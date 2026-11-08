package com.cleanroute.api.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads one setting the way the app does: an environment variable first, then the git-ignored
 * {@code ../.env}. Used only by live tests (tag "eval"); values are never printed.
 */
public final class DotEnv {

    private DotEnv() {
    }

    public static Optional<String> get(String name) {
        String fromEnv = System.getenv(name);
        if (usable(fromEnv)) {
            return Optional.of(fromEnv.trim());
        }
        for (Path file : new Path[] {Path.of("..", ".env"), Path.of(".env")}) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith(name + "=")) {
                        String value = trimmed.substring(name.length() + 1).trim();
                        if (usable(value)) {
                            return Optional.of(value);
                        }
                    }
                }
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean usable(String value) {
        return value != null && !value.isBlank() && !value.trim().startsWith("change_me");
    }
}
