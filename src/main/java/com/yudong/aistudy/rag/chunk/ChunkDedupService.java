package com.yudong.aistudy.rag.chunk;

import java.util.List;

public interface ChunkDedupService {

    List<String> dedup(List<String> chunks);

    String calculateHash(String chunk);
}
