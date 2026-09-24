package com.yudong.aistudy.rag.vectorindex;

import com.yudong.aistudy.config.properties.VectorIndexProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantCollectionInitializerTest {

    private static final String COLLECTION_URL = "http://localhost:6333/collections/document_chunks";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private VectorIndexProperties properties;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        properties = new VectorIndexProperties();
        properties.setEnabled(true);
        properties.setInitializeOnStartup(true);
    }

    @Test
    void skipsInitializationWhenVectorIndexIsDisabled() {
        properties.setEnabled(false);
        server.expect(never(), requestTo(COLLECTION_URL));

        new QdrantCollectionInitializer(restTemplate, properties).run(null);

        server.verify();
    }

    @Test
    void createsCollectionWhenItDoesNotExist() {
        server.expect(requestTo(COLLECTION_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        server.expect(requestTo(COLLECTION_URL)).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"vectors":{"size":1024,"distance":"Cosine"}}
                        """))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));

        new QdrantCollectionInitializer(restTemplate, properties).run(null);

        server.verify();
    }

    @Test
    void acceptsExistingCollectionWithMatchingConfiguration() {
        server.expect(requestTo(COLLECTION_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(collectionResponse(1024, "Cosine"), MediaType.APPLICATION_JSON));

        new QdrantCollectionInitializer(restTemplate, properties).run(null);

        server.verify();
    }

    @Test
    void rejectsExistingCollectionWithDifferentDimension() {
        server.expect(requestTo(COLLECTION_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(collectionResponse(768, "Cosine"), MediaType.APPLICATION_JSON));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new QdrantCollectionInitializer(restTemplate, properties).run(null));

        assertTrue(exception.getMessage().contains("configured=1024, actual=768"));
        server.verify();
    }

    private String collectionResponse(int dimension, String distance) {
        return """
                {"result":{"config":{"params":{"vectors":{"size":%d,"distance":"%s"}}}}}
                """.formatted(dimension, distance);
    }
}
