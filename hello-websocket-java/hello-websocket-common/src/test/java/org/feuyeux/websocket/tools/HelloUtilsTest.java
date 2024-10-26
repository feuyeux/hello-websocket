package org.feuyeux.websocket.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import org.feuyeux.websocket.info.EchoRequest;
import org.feuyeux.websocket.info.EchoResult;
import org.feuyeux.websocket.info.KissRequest;
import org.feuyeux.websocket.info.KissResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HelloUtilsTest {

    @BeforeEach
    void setUp() {
        // Any setup can be done here if needed
    }

    @Test
    void testGetHelloList() {
        List<String> helloList = HelloUtils.getHelloList();
        assertNotNull(helloList);
        assertEquals(7, helloList.size());
        assertTrue(helloList.contains("Hello"));
    }

    @Test
    void testGetAnswerMap() {
        Map<String, String> answerMap = HelloUtils.getAnswerMap();
        assertNotNull(answerMap);
        assertEquals("Thank you very much", answerMap.get("Hello"));
    }

    @Test
    void testBuildLinkRequests() {
        LinkedList<EchoRequest> requests = HelloUtils.buildLinkRequests();
        assertNotNull(requests);
        assertEquals(3, requests.size());
        assertNotNull(requests.getFirst().getId());
    }

    @Test
    void testGetRandomIds() {
        List<String> randomIds = HelloUtils.getRandomIds(5);
        assertNotNull(randomIds);
        assertEquals(5, randomIds.size());
    }

    @Test
    void testBuildResults() {
        List<EchoResult> results = HelloUtils.buildResults("1", "2", "3");
        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals("Bonjour", results.get(0).getKv().get("data").split(",")[0]);
    }

    @Test
    void testBuildResult() {
        EchoResult result = HelloUtils.buildResult("1");
        assertNotNull(result);
        assertEquals("Bonjour", result.getKv().get("data").split(",")[0]);
    }

    @Test
    void testBuildKissRequest() {
        KissRequest request = HelloUtils.buildKissRequest();
        assertNotNull(request);
        KissRequest.Content content = request.getBody().getContent();
        assertNotNull(content.getOsName());
        assertNotNull(content.getOsVersion());
        assertNotNull(content.getOsRelease());
        assertNotNull(content.getOsArchitecture());
    }

    @Test
    void testBuildKissResponse() {
        KissResponse response = HelloUtils.buildKissResponse();
        assertNotNull(response);
        KissResponse.Content content = response.getBody().getContent();
        assertNotNull(content.getLanguage());
        assertNotNull(content.getEncoding());
        assertNotNull(content.getTimeZone());
    }
}