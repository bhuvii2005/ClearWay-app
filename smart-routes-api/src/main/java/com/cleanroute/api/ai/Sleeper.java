package com.cleanroute.api.ai;

import java.time.Duration;

/** Pauses before a retry or between paced requests; replaced in tests so they don't really wait. */
@FunctionalInterface
public interface Sleeper {

    Sleeper REAL = duration -> Thread.sleep(duration.toMillis());

    void sleep(Duration duration) throws InterruptedException;
}
