package org.egov.pgr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SeverityClientTest {

    private static final String URL = "http://severity:8000";

    @Test
    void scoresAndMapsTheResponse() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(requestTo(URL + "/severity"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.description").value("dogs bite"))
                .andExpect(jsonPath("$.complaint_type").value("Street Dogs"))
                .andRespond(withSuccess("{\"severity\":\"High\",\"severity_id\":2,\"confidence\":0.81,"
                        + "\"needs_review\":false,\"model_version\":\"v1\"}", MediaType.APPLICATION_JSON));

        Optional<Map<String, Object>> r = new SeverityClient(URL, rt, new ObjectMapper())
                .score("PG-1", "dogs bite", "Street Dogs");

        assertTrue(r.isPresent());
        assertEquals("High", r.get().get("level"));
        assertEquals(2, r.get().get("levelId"));
        assertEquals(false, r.get().get("needsReview"));
        server.verify();
    }

    @Test
    void failsOpenOnServerError() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer.createServer(rt).expect(requestTo(URL + "/severity")).andRespond(withServerError());
        assertFalse(new SeverityClient(URL, rt, new ObjectMapper()).score("PG-1", "x", "Others").isPresent());
    }

    @Test
    void disabledWhenUrlBlankOrDescriptionEmpty() {
        assertFalse(new SeverityClient("", new RestTemplate(), new ObjectMapper()).score("1", "text", "t").isPresent());
        assertFalse(new SeverityClient(URL, new RestTemplate(), new ObjectMapper()).score("1", " ", "t").isPresent());
    }
}
