package com.statusbeat.statusbeat.service;

import com.statusbeat.statusbeat.constants.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthTemplateService {

    private final TemplateEngine templateEngine;

    public String renderSuccess(String spotifyAuthLink) {
        try {
            Context context = new Context();
            context.setVariable("spotifyAuthLink", spotifyAuthLink);
            return templateEngine.process("slack-oauth-success", context);
        } catch (Exception e) {
            log.error("Failed to render success template", e);
            return getFallbackSuccessHtml(spotifyAuthLink);
        }
    }

    public String renderError(String errorTitle, String errorDescription, String errorDetails) {
        try {
            Context context = new Context();
            context.setVariable("errorTitle", errorTitle);
            context.setVariable("errorDescription", errorDescription);
            context.setVariable("errorDetails", errorDetails);
            return templateEngine.process("slack-oauth-error", context);
        } catch (Exception e) {
            log.error("Failed to render error template", e);
            return getFallbackErrorHtml(errorTitle, errorDetails);
        }
    }

    private String getFallbackSuccessHtml(String spotifyAuthLink) {
        return """
                <html>
                <body style="font-family: sans-serif; text-align: center; padding: 50px;">
                    <h1>Slack Connected!</h1>
                    <p>Now connect your Spotify account:</p>
                    <a href="%s" style="display: inline-block; padding: 10px 20px; background: #1DB954; color: white; text-decoration: none; border-radius: 5px;">Connect Spotify</a>
                </body>
                </html>
                """.formatted(spotifyAuthLink);
    }

    private String getFallbackErrorHtml(String errorTitle, String errorDetails) {
        return """
                <html>
                <body style="font-family: sans-serif; text-align: center; padding: 50px;">
                    <h1>%s</h1>
                    <p>Error: %s</p>
                    <a href="%s" style="display: inline-block; padding: 10px 20px; background: #1DB954; color: white; text-decoration: none; border-radius: 5px;">Try Again</a>
                </body>
                </html>
                """.formatted(errorTitle, errorDetails, AppConstants.SLACK_INSTALL_PATH);
    }
}
