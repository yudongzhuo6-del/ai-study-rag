package com.yudong.aistudy.rag.split;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSplitterTest {

    @Test
    void overlapStartsAtSentenceBoundaryWhenOneIsNearby() {
        String text = "第一句介绍基础内容。第二句继续补充上下文。第三句说明新的内容。第四句用于结束测试。";

        List<String> chunks = TextSplitter.splitByLength(text, 24, 10);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.get(1).startsWith("第二句") || chunks.get(1).startsWith("第三句"));
        assertFalse(chunks.get(1).startsWith("续补充"));
    }

    @Test
    void titleIsAddedWhenItOnlyAppearsInsideChunkBody() {
        String title = "第二章 JVM";
        String section = title + "\n\n正文内容";
        List<String> chunks = List.of("正文中提到了" + title + "但它不是前缀");

        List<String> inherited = TextSplitter.applyTitleInheritance(section, chunks);

        assertEquals(title + "\n\n" + chunks.get(0), inherited.get(0));
    }
}
