package io.github.anjali.notifyflow.management.service.dispatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.exception.ProviderDispatchException;

/** SendGrid v3 Mail Send adapter. Credentials stay in provider config or environment only. */
@Component
public class SendGridDispatcher implements NotificationDispatcher {

    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.sendgrid.com/v3/mail/send");
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Duration timeout;

    public SendGridDispatcher() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper(),
                URI.create(System.getenv().getOrDefault("SENDGRID_BASE_URL", DEFAULT_ENDPOINT.toString())),
                Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("SENDGRID_TIMEOUT_SECONDS", "10"))));
    }

    SendGridDispatcher(HttpClient httpClient, ObjectMapper objectMapper, URI endpoint, Duration timeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.timeout = timeout;
    }

    @Override
    public void dispatch(NotificationProvider provider, String recipient, String subject, String body) {
        Map<String, String> config = parseConfig(provider.getConfig());
        // String apiKey = config.getOrDefault("apiKey", System.getenv("SENDGRID_API_KEY"));
        String apiKey = config.get("apiKey");

if (isBlank(apiKey)) {
    apiKey = System.getenv("SENDGRID_API_KEY");
}
        String fromEmail = config.get("fromEmail");
        String fromName = config.get("fromName");

      
System.out.println("===== SENDGRID DEBUG =====");
System.out.println("Provider config present: " + (provider.getConfig() != null));
System.out.println("fromEmail present: " + !isBlank(fromEmail));
System.out.println("fromName present: " + !isBlank(fromName));
System.out.println("API key present: " + !isBlank(apiKey));
System.out.println("Environment API key present: "
        + !isBlank(System.getenv("SENDGRID_API_KEY")));
System.out.println("==========================");

        if (isBlank(apiKey) || isBlank(fromEmail)) {
            throw new ProviderDispatchException("SendGrid configuration requires fromEmail and an API key");
        }

        try {
            Map<String, Object> from = fromName == null || fromName.isBlank()
                    ? Map.of("email", fromEmail) : Map.of("email", fromEmail, "name", fromName);
            Map<String, Object> payload = Map.of(
                    "personalizations", java.util.List.of(Map.of("to", java.util.List.of(Map.of("email", recipient)))),
                    "from", from,
                    "subject", subject == null ? "" : subject,
                    "content", java.util.List.of(Map.of("type", "text/plain", "value", body == null ? "" : body)));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderDispatchException("SendGrid rejected the notification (HTTP " + response.statusCode() + ")");
            }
        } catch (ProviderDispatchException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ProviderDispatchException("SendGrid request timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderDispatchException("SendGrid request was interrupted", e);
        } catch (Exception e) {
            throw new ProviderDispatchException("SendGrid request failed", e);
        }
    }

    private Map<String, String> parseConfig(String rawConfig) {
        if (isBlank(rawConfig)) {
            throw new ProviderDispatchException("SendGrid provider configuration is missing");
        }
        try {
            return objectMapper.readValue(rawConfig, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ProviderDispatchException("SendGrid provider configuration is invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
