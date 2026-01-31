package com.statusbeat.statusbeat.controller;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_servlet.SlackAppServlet;
import jakarta.servlet.annotation.WebServlet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WebServlet("/slack/events")
public class SlackController extends SlackAppServlet {

    public SlackController(App app) {
        super(app);
        log.info("SlackController servlet created and initialized");
    }
}
