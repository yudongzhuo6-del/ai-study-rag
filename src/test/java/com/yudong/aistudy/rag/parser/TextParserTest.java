package com.yudong.aistudy.rag.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextParserTest {

    @Test
    void parsesAndCleansPlainTextFromStream() throws Exception {
        String source = "first   line\r\n\r\n\r\nsecond\tline";

        String content = TextParser.parse(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                "notes.txt"
        );

        assertEquals("first line\n\nsecond line", content);
    }
}
