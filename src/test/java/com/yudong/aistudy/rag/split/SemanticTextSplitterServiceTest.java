package com.yudong.aistudy.rag.split;

import com.yudong.aistudy.config.properties.SplitterProperties;
import com.yudong.aistudy.rag.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SemanticTextSplitterServiceTest {

    @Test
    void firstBodyParagraphStaysWithItsHeadingWithoutSimilarityCheck() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        SplitterProperties properties = new SplitterProperties();
        SemanticTextSplitterService service = new SemanticTextSplitterService(embeddingService, properties);

        List<String> sections = service.splitBySemanticBoundary("第二章 JVM\n\nJVM运行时数据区包括堆和虚拟机栈。");

        assertEquals(1, sections.size());
        assertTrue(sections.get(0).startsWith("第二章 JVM\n\n"));
        verifyNoInteractions(embeddingService);
    }
}
