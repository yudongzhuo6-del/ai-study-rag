package com.yudong.aistudy.rag.query;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryRewriteResult {

    private String originalQuestion;

    private String normalizedQuestion;

    private String vectorQuery;

    private String keywordQuery;

    private List<String> expandedKeywords = new ArrayList<>();

    private Boolean fallback;

    private String fallbackReason;
}
