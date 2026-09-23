package com.yudong.aistudy.service;

import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import com.yudong.aistudy.model.vo.chat.ChatAnswerVO;

public interface ChatService {

    ChatAnswerVO ask(String question, RetrieveFilter filter);

}
