package com.loyiso.booking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminKeyInterceptorTest {

    private boolean call(AdminKeyInterceptor interceptor, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/branches");
        if (key != null) {
            request.addHeader(AdminKeyInterceptor.HEADER, key);
        }
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void theAdminApiLooksNonExistentWhenNoKeyIsConfigured() {
        AdminKeyInterceptor off = new AdminKeyInterceptor("");

        assertThatThrownBy(() -> call(off, "anything")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> call(new AdminKeyInterceptor("   "), null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aMissingOrWrongKeyIsRefused() {
        AdminKeyInterceptor on = new AdminKeyInterceptor("the-real-key");

        assertThatThrownBy(() -> call(on, null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> call(on, "a-guess")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void theRightKeyPasses() {
        assertThat(call(new AdminKeyInterceptor("the-real-key"), "the-real-key")).isTrue();
    }
}