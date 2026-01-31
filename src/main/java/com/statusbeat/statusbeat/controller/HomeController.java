package com.statusbeat.statusbeat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/success")
    public String success(Model model) {
        model.addAttribute("message", "Successfully connected! Your music will now sync with your Slack status.");
        return "success";
    }

    @GetMapping("/error")
    public String error(@RequestParam(value = "message", required = false) String message, Model model) {
        model.addAttribute("error", message != null ? message : "An error occurred");
        return "error";
    }
}
