package com.yudong.aistudy.rag.llm;

import com.yudong.aistudy.config.properties.LlmProperties;
import com.yudong.aistudy.model.dto.llm.LlmChatRequestDTO;
import com.yudong.aistudy.model.dto.llm.LlmChatResponseDTO;
import com.yudong.aistudy.model.dto.llm.LlmMessageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Component
public class RealLlmService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LlmProperties llmProperties;

    public String chat(String question, String context) {
        String prompt = """
                你是一个严格基于参考资料回答的知识库问答助手。

                回答要求：
                1. 只能使用【参考资料】中的信息回答，不要编造。
                2. 每个关键结论后必须标注来源编号，例如 [1]。
                3. 如果一个结论来自多个资料，可以使用多个编号，例如 [1][2]。
                4. 不要引用不存在的编号。
                5. 如果参考资料不足以回答，请明确回答“根据当前资料无法确定”。
                6. 回答要简洁、准确，优先直接回答用户问题。

                【参考资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        LlmMessageDTO systemMessage = new LlmMessageDTO(
                "system",
                "你是一个严谨的知识库问答助手，必须基于参考资料回答并标注引用来源。"
        );
        LlmMessageDTO userMessage = new LlmMessageDTO("user", prompt);

        LlmChatRequestDTO requestDTO = new LlmChatRequestDTO();
        requestDTO.setModel(llmProperties.getModel());
        requestDTO.setMessages(Arrays.asList(systemMessage, userMessage));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiKey());

        HttpEntity<LlmChatRequestDTO> entity = new HttpEntity<>(requestDTO, headers);

        ResponseEntity<LlmChatResponseDTO> response = restTemplate.exchange(
                llmProperties.getBaseUrl(),
                HttpMethod.POST,
                entity,
                LlmChatResponseDTO.class
        );

        LlmChatResponseDTO body = response.getBody();
        if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
            return "大模型返回为空";
        }

        if (body.getChoices().get(0).getMessage() == null) {
            return "大模型返回格式异常";
        }

        return body.getChoices().get(0).getMessage().getContent();
    }

    public String repairCitation(String question, String context, String answer) {
        String prompt = """
                请只修正回答中的引用编号，不要改变原回答的事实内容和表达。

                修正规则：
                1. 只能使用【参考资料】中存在的编号，例如 [1]、[2]。
                2. 如果某个关键结论缺少引用，请根据参考资料补上最合适的编号。
                3. 如果引用了不存在的编号，请删除或替换为存在的编号。
                4. 不要新增参考资料中没有的信息。
                5. 只输出修正后的回答正文，不要解释修正过程。

                【参考资料】
                %s

                【用户问题】
                %s

                【待修正回答】
                %s
                """.formatted(context, question, answer);

        LlmMessageDTO systemMessage = new LlmMessageDTO(
                "system",
                "你是一个引用格式修正助手，只修正引用编号，不改写事实内容。"
        );
        LlmMessageDTO userMessage = new LlmMessageDTO("user", prompt);

        LlmChatRequestDTO requestDTO = new LlmChatRequestDTO();
        requestDTO.setModel(llmProperties.getModel());
        requestDTO.setMessages(Arrays.asList(systemMessage, userMessage));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiKey());

        HttpEntity<LlmChatRequestDTO> entity = new HttpEntity<>(requestDTO, headers);

        ResponseEntity<LlmChatResponseDTO> response = restTemplate.exchange(
                llmProperties.getBaseUrl(),
                HttpMethod.POST,
                entity,
                LlmChatResponseDTO.class
        );

        LlmChatResponseDTO body = response.getBody();
        if (body == null || body.getChoices() == null || body.getChoices().isEmpty()
                || body.getChoices().get(0).getMessage() == null) {
            return answer;
        }

        String repairedAnswer = body.getChoices().get(0).getMessage().getContent();
        if (repairedAnswer == null || repairedAnswer.trim().isEmpty()) {
            return answer;
        }

        return repairedAnswer;
    }
}
