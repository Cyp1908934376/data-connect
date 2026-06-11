package com.dataconnect.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    @Value("${app.auth.enabled:true}")
    private boolean authEnabled;

    @PostConstruct
    public void init() {
        log.info("登录认证: {}", authEnabled ? "已启用" : "已关闭");
    }

    private static final Map<String, String> TOKEN_STORE = new ConcurrentHashMap<>();
    private static final String COOKIE_NAME = "dc_token";
    private static final String LOGIN_PATH = "/login";
    private static final String[] EXCLUDE_PATHS = {
            "/login", "/static/", "/h2-console", "/css/", "/js/", "/fonts/", "/codemirror/",
            "/doc.html", "/swagger-ui/", "/v3/api-docs", "/webjars/"
    };

    public static String createToken(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        TOKEN_STORE.put(token, username);
        return token;
    }

    public static void removeToken(String token) {
        TOKEN_STORE.remove(token);
    }

    public static boolean isValidToken(String token) {
        return token != null && TOKEN_STORE.containsKey(token);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!authEnabled) {
            return true;
        }

        String path = request.getRequestURI();

        for (String exclude : EXCLUDE_PATHS) {
            if (path.startsWith(exclude)) {
                return true;
            }
        }

        String token = getCookieValue(request, COOKIE_NAME);
        if (isValidToken(token)) {
            return true;
        }

        if (isApiRequest(request)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return false;
        }

        response.sendRedirect(LOGIN_PATH);
        return false;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
