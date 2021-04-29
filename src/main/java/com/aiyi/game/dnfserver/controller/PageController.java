package com.aiyi.game.dnfserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping
    public String index(){
        return "index";
    }

    @GetMapping("register")
    public String register(){
        return "register";
    }

}
