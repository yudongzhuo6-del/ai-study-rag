package com.yudong.aistudy.model.dto.eval;

import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import lombok.Data;

@Data
public class EvalRunCreateDTO {

    private Long datasetId;

    private String runName;

    private RetrieveFilter filter;
}
