package com.yudong.aistudy.rag.citation;

import com.yudong.aistudy.model.vo.chat.ChatSourceVO;

import java.util.List;

public interface CitationValidator {

    CitationValidationResult validate(String answer, List<ChatSourceVO> sources);
}
