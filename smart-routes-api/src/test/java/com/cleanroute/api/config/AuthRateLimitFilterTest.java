package com.cleanroute.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {

    private static MockHttpServletResponse call(AuthRateLimitFilter filter, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksAfterLimit_OnLoginOnly() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(3);

        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(filter, "POST", "/api/v1/auth/login").getStatus());
        }
        MockHttpServletResponse blocked = call(filter, "POST", "/api/v1/auth/login");
        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));

        // Other endpoints and methods are not limited.
        assertEquals(200, call(filter, "GET", "/api/v1/auth/login").getStatus());
        assertEquals(200, call(filter, "POST", "/api/v1/routes").getStatus());
        // Signup has its own counter.
        assertEquals(200, call(filter, "POST", "/api/v1/auth/signup").getStatus());
    }
}
