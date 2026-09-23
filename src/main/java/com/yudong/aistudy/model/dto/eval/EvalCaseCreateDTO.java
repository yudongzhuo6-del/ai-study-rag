package com.yudong.aistudy.model.dto.eval;

import lombok.Data;

import java.util.List;

@Data
public class EvalCaseCreateDTO {

    private Long datasetId;

    private String question;

    private String expectedAnswer;

    private List<GoldenChunkDTO> goldenChunks;

    private List<String> requiredKeywords;

    private String questionType;

    private String difficulty;
}
