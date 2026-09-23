package com.yudong.aistudy.rag.citation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CitationValidationResult {

    private boolean valid;

    private boolean missingCitation;

    private List<Integer> citedIndexes = new ArrayList<>();

    private List<Integer> invalidIndexes = new ArrayList<>();
}
