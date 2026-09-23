package com.yudong.aistudy.rag.embedding;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class FakeEmbeddingService implements EmbeddingService {

    @Override
    public String embed(String text) {

        Random random = new Random();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            sb.append(random.nextDouble()).append(",");
        }

        return sb.toString();
    }
}
