package com.cleanroute.api.support;

import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

/**
 * Throwaway database for integration tests, built from the same {@code db/Dockerfile}
 * that docker compose uses (PostGIS plus pgvector), so tests run on what production runs.
 */
public final class TestDatabase {

    private static final Path DOCKERFILE_DIR = Path.of("..", "db");

    private static DockerImageName image;

    private TestDatabase() {
    }

    public static PostgreSQLContainer newContainer() {
        return new PostgreSQLContainer(image());
    }

    private static synchronized DockerImageName image() {
        if (image == null) {
            // A fixed name that is kept after the run, so Docker's layer cache makes later builds instant.
            var build = new ImageFromDockerfile("clearway-db-test", false)
                    .withFileFromPath(".", DOCKERFILE_DIR);
            image = DockerImageName.parse(build.get()).asCompatibleSubstituteFor("postgres");
        }
        return image;
    }
}
