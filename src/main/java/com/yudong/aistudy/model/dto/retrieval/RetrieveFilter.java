package com.yudong.aistudy.model.dto.retrieval;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class RetrieveFilter {

    private Long documentId;

    private Long userId;

    private Long knowledgeBaseId;

    private List<Long> knowledgeBaseIds;

    private List<Long> documentIds;

    private Integer status = 1;

    private String documentType;

    private List<String> documentTypes;

    public List<Long> effectiveDocumentIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (documentId != null) {
            ids.add(documentId);
        }

        if (documentIds != null) {
            for (Long id : documentIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }

        return new ArrayList<>(ids);
    }

    public boolean hasDocumentIds() {
        return !effectiveDocumentIds().isEmpty();
    }

    public List<Long> effectiveKnowledgeBaseIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (knowledgeBaseId != null) {
            ids.add(knowledgeBaseId);
        }

        if (knowledgeBaseIds != null) {
            for (Long id : knowledgeBaseIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }

        return new ArrayList<>(ids);
    }

    public boolean hasKnowledgeBaseIds() {
        return !effectiveKnowledgeBaseIds().isEmpty();
    }

    public List<String> effectiveDocumentTypes() {
        Set<String> types = new LinkedHashSet<>();
        if (documentType != null && !documentType.trim().isEmpty()) {
            types.add(documentType.trim());
        }

        if (documentTypes != null) {
            for (String type : documentTypes) {
                if (type != null && !type.trim().isEmpty()) {
                    types.add(type.trim());
                }
            }
        }

        return new ArrayList<>(types);
    }

    public boolean hasDocumentTypes() {
        return !effectiveDocumentTypes().isEmpty();
    }
}
