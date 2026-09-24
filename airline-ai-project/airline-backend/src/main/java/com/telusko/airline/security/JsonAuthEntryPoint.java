package com.telusko.airline.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telusko.airline.dto.ApiDtos.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Turns the two security refusals into JSON with the right status.
 *
 * Without this, Spring Security answers both with a bare 403 and an empty body. That is wrong
 * twice over. A missing or expired token is a 401, not a 403, and the frontend branches on
 * exactly that: it clears the stored session on a 401 and sends the passenger to sign in
 * again. Answering 403 instead leaves them holding a dead token, getting refusals on every
 * page, with nothing telling them to log in.
 *
 * The empty body is the other half. Every other error in this application comes back as
 * {@code error} and {@code detail}, and the frontend reads those fields. An empty response
 * meant the user saw "Something went wrong" for a problem the server knew the answer to.
 */
@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json = new ObjectMapper();

    /** No credentials, or credentials that did not work. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated",
                "Please sign in to continue.");
    }

    /** Signed in, but not allowed here. A passenger reaching an admin endpoint. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden",
                "You do not have access to this.");
    }

    private void write(HttpServletResponse response, int status, String error, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), new ApiError(error, detail));
    }
}
