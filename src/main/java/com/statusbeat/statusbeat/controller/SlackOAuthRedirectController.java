package com.statusbeat.statusbeat.controller;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_servlet.SlackOAuthAppServlet;
import jakarta.servlet.annotation.WebServlet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WebServlet("/slack/oauth_redirect")
public class SlackOAuthRedirectController extends SlackOAuthAppServlet {

    public SlackOAuthRedirectController(App app) {
        super(app);
        log.info("SlackOAuthRedirectController initialized at /slack/oauth_redirect");
    }
}
