package com.company.taskmanagementplatform.common.mail;

import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers the demo's mail over a provider's HTTP API.
 *
 * <p>{@code @Profile("demo")}, so production never sees this class. Production sends over SMTP and
 * {@link MailConfig} is what wires it; nothing here changes that, and nothing here is reachable by
 * setting a variable in a production environment.
 *
 * <h2>Why this needed no change to MailConfig</h2>
 *
 * <p>Because {@link MailConfig} already said it would not: <em>"a deployment that wants a provider's
 * own API rather than SMTP contributes one and deletes nothing"</em>. This is that contribution. The
 * port was designed for exactly this and the design held — no new enum constant, no new branch in
 * the selector, and not a line of the SMTP path touched.
 *
 * <h2>Primary rather than conditional, for the reason the test config gives</h2>
 *
 * <p>{@code MailConfig}'s bean is {@code @ConditionalOnMissingBean}, which is evaluated in
 * registration order — an ordering a deployment should not have to reason about. {@code TestMailConfig}
 * settled the same question the same way years of this repository ago: being primary decides it
 * whichever way the ordering falls. Both beans exist; this one is injected.
 *
 * <h2>What happens without an API key</h2>
 *
 * <p>Nothing is registered at all, and {@code MailConfig}'s own selection stands. Under the demo
 * profile that is the logging transport, which writes the verification and reset links to the log
 * with the token intact, because {@code application-demo.properties} switches {@code
 * app.mail.log-tokens} on. So an unconfigured demo still works — somebody copies the link out of the
 * Render log — and a configured one delivers properly. A missing key is a downgrade, not a failure,
 * which is the right shape for a demo and would be quite the wrong one for production.
 *
 * <p>The condition is an expression rather than {@code @ConditionalOnProperty}, and the difference
 * matters for the reason {@code MailConfig} gives about {@code spring.mail.host}: a property set to
 * an empty string counts as <em>present</em>, and this application's own defaults set it that way,
 * so the simpler annotation would register a transport with no key and collect 401s from the
 * provider. It also has to stay conditional so that a test which contributes its own primary
 * transport does not end up with two of them.
 */
@Configuration
@Profile("demo")
@ConditionalOnExpression("'${app.mail.http.api-key:}' != ''")
@EnableConfigurationProperties(HttpMailProperties.class)
class DemoMailConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoMailConfig.class);

    @Bean
    @Primary
    MailSender httpMailSender(HttpMailProperties http, MailProperties mail, JsonMapper json) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(http.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        log.info("Mail is delivered over HTTP to {}.", http.url());
        return new HttpMailSender(client, http, mail, json);
    }
}
