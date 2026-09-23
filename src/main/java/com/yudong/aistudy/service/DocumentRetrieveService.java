package com.yudong.aistudy.service;

import com.yudong.aistudy.model.dto.retrieval.RetrieveResult;
import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;

public interface DocumentRetrieveService {

    RetrieveResult retrieve(String question, RetrieveFilter filter);

}
