package com.yudong.aistudy.model.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class RetrieveResult {

    private List<RetrievedChunk> chunks;

    private Integer vectorDimension;
}
