package com.cleanroute.api.service;

import com.cleanroute.api.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteValidationTest {

    @Test
    void validCoordinates_Pass() {
        assertDoesNotThrow(() -> RouteService.validateCoordinates(
                List.of(List.of(77.2, 28.6), List.of(-180.0, 90.0))));
    }

    @Test
    void invalidCoordinates_AreRejected() {
        assertThrows(ApiException.class, () -> RouteService.validateCoordinates(List.of(List.of(77.2, 28.6))));
        assertThrows(ApiException.class, () -> RouteService.validateCoordinates(
                List.of(List.of(77.2, 28.6), List.of(200.0, 10.0))));
        assertThrows(ApiException.class, () -> RouteService.validateCoordinates(
                List.of(List.of(77.2, 28.6), List.of(10.0, -91.0))));
        assertThrows(ApiException.class, () -> RouteService.validateCoordinates(
                List.of(List.of(77.2, 28.6), List.of(1.0))));
    }
}
