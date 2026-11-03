package com.aiyi.game.dnfserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import com.aiyi.game.dnfserver.conf.NoLogin;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class PageController {
    @Value("${dnf.frontend-dir:webui2/dist}")
    private String frontendDir;

    @GetMapping("/")
    @NoLogin
    public ModelAndView index(HttpServletResponse response) throws IOException {
        return renderAppOrLegacy(response);
    }

    @GetMapping("register")
    @NoLogin
    public String register(){
        return "register";
    }

    @GetMapping({"/login", "/admin", "/admin/**", "/home", "/about-clan", "/about-me", "/faq", "/gallery", "/matches", "/matches-single", "/players", "/blog", "/blog-single", "/contact", "/index-2", "/notice"})
    @NoLogin
    public ModelAndView app(HttpServletResponse response) throws IOException {
        return renderAppOrLegacy(response);
    }

    private ModelAndView renderAppOrLegacy(HttpServletResponse response) throws IOException {
        ClassPathResource frontend = new ClassPathResource("static/index.html");
        Path localFrontend = Paths.get(frontendDir, "index.html");
        if (!frontend.exists() && !Files.isRegularFile(localFrontend)) return new ModelAndView("index");
        response.setContentType("text/html;charset=UTF-8");
        try (InputStream input = frontend.exists() ? frontend.getInputStream() : Files.newInputStream(localFrontend)) {
            StreamUtils.copy(input, response.getOutputStream());
        }
        return null;
    }

}
