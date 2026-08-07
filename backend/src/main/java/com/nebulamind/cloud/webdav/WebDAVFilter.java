package com.nebulamind.cloud.webdav;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class WebDAVFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        if (!uri.startsWith("/webdav/") && !uri.equals("/webdav")) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod().toUpperCase();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            httpResponse.setHeader("Allow", "PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, COPY, MOVE, OPTIONS");
            httpResponse.setHeader("DAV", "1, 2");
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        if ("PROPFIND".equalsIgnoreCase(method) || "MKCOL".equalsIgnoreCase(method) 
                || "COPY".equalsIgnoreCase(method) || "MOVE".equalsIgnoreCase(method)) {
            HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getMethod() {
                    return "GET";
                }
            };
            wrappedRequest.setAttribute("webdav.method", method);
            chain.doFilter(wrappedRequest, response);
            return;
        }

        chain.doFilter(request, response);
    }
}
