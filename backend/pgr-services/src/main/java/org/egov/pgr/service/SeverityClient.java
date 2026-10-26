package org.egov.pgr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client for the severity-service ({@code backend/severity-service}): scores a new complaint
 * Low / Medium / High / Critical. Advisory only — complaint filing must never depend on it, so a
 * blank URL (feature off), a timeout, or any error all yield {@link Optional#empty()}.
 */
@Slf4j
@Component
public class SeverityClient {

    private final String baseUrl;
    private final RestTemplate http;
    private final ObjectMapper mapper;

    @Autowired
    public SeverityClient(@Value("${pgr.severity.url:}") String baseUrl,
                          @Value("${pgr.severity.connect-timeout-ms:500}") int connectTimeoutMs,
                          @Value("${pgr.severity.read-timeout-ms:3000}") int readTimeoutMs,
                          ObjectMapper mapper) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.http = new RestTemplate(factory);
        this.mapper = mapper;
    }

    /** Test seam: the same client over a given RestTemplate. */
    SeverityClient(String baseUrl, RestTemplate http, ObjectMapper mapper) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.http = http;
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return !baseUrl.isEmpty();
    }

    /**
     * @param complaintType the PGR complaint type display name (not the service code)
     * @return the severity block to store on the complaint, or empty when unavailable
     */
    public Optional<Map<String, Object>> score(String complaintId, String description, String complaintType) {
        if (!isEnabled() || description == null || description.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("complaint_id", complaintId);
            body.put("description", description);
            body.put("complaint_type", complaintType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String raw = http.postForObject(baseUrl + "/severity",
                    new HttpEntity<>(mapper.writeValueAsString(body), headers), String.class);
            if (raw == null) {
                return Optional.empty();
            }
            JsonNode n = mapper.readTree(raw);
            if (!n.hasNonNull("severity")) {
                return Optional.empty();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("level", n.get("severity").asText());
            out.put("levelId", n.path("severity_id").asInt());
            out.put("confidence", n.path("confidence").asDouble());
            out.put("needsReview", n.path("needs_review").asBoolean());
            out.put("modelVersion", n.path("model_version").asText(null));
            return Optional.of(out);
        } catch (Exception e) {
            log.warn("Severity scoring skipped for complaint {}: {}", complaintId, e.toString());
            return Optional.empty();
        }
    }
}
