package com.dataconnect.controller;

import com.dataconnect.config.AuthInterceptor;
import com.dataconnect.dto.ApiResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class LoginController {

    private static final String COOKIE_NAME = "dc_token";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    @ResponseBody
    public ApiResponse<String> doLogin(@RequestParam String username,
                                       @RequestParam String password,
                                       HttpServletResponse response) {
        if (USERNAME.equals(username) && PASSWORD.equals(password)) {
            String token = AuthInterceptor.createToken(username);
            Cookie cookie = new Cookie(COOKIE_NAME, token);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(24 * 60 * 60);
            response.addCookie(cookie);
            return ApiResponse.success("登录成功");
        }
        return ApiResponse.error("用户名或密码错误");
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        String token = getCookieValue(request, COOKIE_NAME);
        if (token != null) {
            AuthInterceptor.removeToken(token);
        }
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/login";
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
