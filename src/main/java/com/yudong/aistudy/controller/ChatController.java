package com.yudong.aistudy.controller;

import com.yudong.aistudy.common.result.Result;
import com.yudong.aistudy.model.dto.chat.ChatAskDTO;
import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import com.yudong.aistudy.model.vo.chat.ChatAnswerVO;
import com.yudong.aistudy.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/ask")
    public Result<ChatAnswerVO> ask(@RequestBody ChatAskDTO dto) {
        RetrieveFilter filter = new RetrieveFilter();
        filter.setUserId(dto.getUserId());
        filter.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        filter.setKnowledgeBaseIds(dto.getKnowledgeBaseIds());
        filter.setDocumentId(dto.getDocumentId());
        filter.setDocumentIds(dto.getDocumentIds());
        filter.setDocumentType(dto.getDocumentType());
        filter.setDocumentTypes(dto.getDocumentTypes());

        ChatAnswerVO vo = chatService.ask(dto.getQuestion(), filter);
        return Result.ok(vo);
    }
}
