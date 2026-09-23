package com.yudong.aistudy.model.dto.eval;

import lombok.Data;

@Data
public class EvalDatasetCreateDTO {

    private String name;

    private String description;

    private Long knowledgeBaseId;
}
