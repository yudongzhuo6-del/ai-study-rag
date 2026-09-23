package com.yudong.aistudy.service.impl;

import com.yudong.aistudy.config.properties.CitationProperties;
import com.yudong.aistudy.model.dto.retrieval.RetrieveResult;
import com.yudong.aistudy.model.dto.retrieval.RetrieveFilter;
import com.yudong.aistudy.model.dto.retrieval.RetrievedChunk;
import com.yudong.aistudy.model.entity.DocumentChunk;
import com.yudong.aistudy.model.vo.chat.ChatAnswerVO;
import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import com.yudong.aistudy.rag.citation.CitationValidationResult;
import com.yudong.aistudy.rag.citation.CitationValidator;
import com.yudong.aistudy.rag.compression.CompressedContext;
import com.yudong.aistudy.rag.compression.ContextCompressionService;
import com.yudong.aistudy.rag.llm.RealLlmService;
import com.yudong.aistudy.service.ChatService;
import com.yudong.aistudy.service.DocumentRetrieveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private DocumentRetrieveService documentRetrieveService;

    @Autowired
    private RealLlmService realLlmService;

    @Autowired
    private ContextCompressionService contextCompressionService;

    @Autowired
    private CitationValidator citationValidator;

    @Autowired
    private CitationProperties citationProperties;

    @Override
    public ChatAnswerVO ask(String question, RetrieveFilter filter) {
        RetrieveResult retrieveResult = documentRetrieveService.retrieve(question, filter);
        List<RetrievedChunk> retrievedChunks = retrieveResult.getChunks();
        List<CompressedContext> compressedContexts = contextCompressionService.compress(question, retrievedChunks);

        StringBuilder contextBuilder = new StringBuilder();
        List<ChatSourceVO> sources = new ArrayList<>();

        int citationIndex = 1;
        for (CompressedContext compressedContext : compressedContexts) {
            RetrievedChunk retrievedChunk = compressedContext.getRetrievedChunk();
            DocumentChunk chunk = retrievedChunk.getChunk();
            contextBuilder
                    .append("[")
                    .append(citationIndex)
                    .append("]\n")
                    .append("documentId: ")
                    .append(chunk.getDocumentId())
                    .append("\n")
                    .append("chunkIndex: ")
                    .append(chunk.getChunkIndex())
                    .append("\n")
                    .append("content:\n")
                    .append(compressedContext.getCompressedContent())
                    .append("\n\n");

            ChatSourceVO source = new ChatSourceVO();
            source.setCitationIndex(citationIndex);
            source.setDocumentId(chunk.getDocumentId());
            source.setChunkIndex(chunk.getChunkIndex());
            source.setContent(chunk.getContent());
            source.setCompressedContent(compressedContext.getCompressedContent());
            source.setSimilarityScore(retrievedChunk.getSimilarityScore());
            source.setKeywordScore(retrievedChunk.getKeywordScore());
            source.setFinalScore(retrievedChunk.getFinalScore());
            source.setRetrieveSource(retrievedChunk.getRetrieveSource());
            source.setVectorRetrieveSource(retrievedChunk.getVectorRetrieveSource());
            source.setKeywordRecallScore(retrievedChunk.getKeywordRecallScore());
            source.setHybridRecallScore(retrievedChunk.getHybridRecallScore());
            sources.add(source);
            citationIndex++;
        }

        String context = contextBuilder.toString();
        String answer = realLlmService.chat(question, context);
        CitationValidationResult citationValidationResult = citationValidator.validate(answer, sources);
        log.info(
                "Citation validation result: valid={}, missingCitation={}, citedIndexes={}, invalidIndexes={}, sourceCount={}",
                citationValidationResult.isValid(),
                citationValidationResult.isMissingCitation(),
                citationValidationResult.getCitedIndexes(),
                citationValidationResult.getInvalidIndexes(),
                sources.size()
        );

        if (citationProperties.isRepairEnabled() && !citationValidationResult.isValid()) {
            try {
                String repairedAnswer = realLlmService.repairCitation(question, context, answer);
                CitationValidationResult repairedValidationResult = citationValidator.validate(repairedAnswer, sources);
                log.info(
                        "Citation repair result: beforeValid={}, afterValid={}, beforeMissingCitation={}, afterMissingCitation={}, beforeInvalidIndexes={}, afterInvalidIndexes={}",
                        citationValidationResult.isValid(),
                        repairedValidationResult.isValid(),
                        citationValidationResult.isMissingCitation(),
                        repairedValidationResult.isMissingCitation(),
                        citationValidationResult.getInvalidIndexes(),
                        repairedValidationResult.getInvalidIndexes()
                );
                if (repairedValidationResult.isValid()) {
                    answer = repairedAnswer;
                } else {
                    log.warn("Citation repair skipped because repaired answer is still invalid.");
                }
            } catch (Exception e) {
                log.warn("Citation repair failed, use original answer.", e);
            }
        }

        ChatAnswerVO vo = new ChatAnswerVO();
        vo.setQuestion(question);
        vo.setAnswer(answer);
        vo.setRetrievedCount(retrievedChunks.size());
        vo.setVectorDimension(retrieveResult.getVectorDimension());
        vo.setSources(sources);

        return vo;
    }
}
