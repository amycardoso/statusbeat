package com.statusbeat.statusbeat.controller;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_servlet.SlackOAuthAppServlet;
import jakarta.servlet.annotation.WebServlet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WebServlet("/slack/install")
public class SlackOAuthInstallController extends SlackOAuthAppServlet {

    public SlackOAuthInstallController(App app) {
        super(app);
        log.info("SlackOAuthInstallController initialized at /slack/install");
    }
}
