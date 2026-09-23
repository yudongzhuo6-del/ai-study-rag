package com.yudong.aistudy.rag.query;

import com.yudong.aistudy.config.properties.QueryRewriteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class QueryRewriteRouterServiceImpl implements QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteRouterServiceImpl.class);

    private final RuleBasedQueryRewriteServiceImpl ruleBasedQueryRewriteService;

    private final LlmQueryRewriteServiceImpl llmQueryRewriteService;

    private final QueryRewriteProperties queryRewriteProperties;

    public QueryRewriteRouterServiceImpl(RuleBasedQueryRewriteServiceImpl ruleBasedQueryRewriteService,
                                         LlmQueryRewriteServiceImpl llmQueryRewriteService,
                                         QueryRewriteProperties queryRewriteProperties) {
        this.ruleBasedQueryRewriteService = ruleBasedQueryRewriteService;
        this.llmQueryRewriteService = llmQueryRewriteService;
        this.queryRewriteProperties = queryRewriteProperties;
    }

    @Override
    public QueryRewriteResult rewrite(String question) {
        if (!queryRewriteProperties.isLlmEnabled()) {
            return ruleBasedQueryRewriteService.rewrite(question);
        }

        try {
            return llmQueryRewriteService.rewrite(question);
        } catch (Exception e) {
            log.warn("LLM query rewrite failed, fallback to rule-based query rewrite.", e);
            return ruleBasedQueryRewriteService.rewrite(question);
        }
    }
}
