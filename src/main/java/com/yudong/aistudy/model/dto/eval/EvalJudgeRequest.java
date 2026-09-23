package com.yudong.aistudy.model.dto.eval;

import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import lombok.Data;

import java.util.List;

@Data
public class EvalJudgeRequest {

    private String question;

    private String expectedAnswer;

    private String requiredKeywords;

    private List<ChatSourceVO> sources;

    private String actualAnswer;

    private boolean citationValid;

    private boolean missingCitation;

    private List<Integer> invalidCitations;
}
