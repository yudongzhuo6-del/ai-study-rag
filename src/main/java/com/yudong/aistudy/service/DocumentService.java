package com.yudong.aistudy.service;

import com.yudong.aistudy.model.dto.document.DocumentAddDTO;
import com.yudong.aistudy.model.entity.Document;
import com.yudong.aistudy.model.vo.document.DocumentDeleteResultVO;

public interface DocumentService {

    Long add(DocumentAddDTO dto);

    Document findByFileHash(Long knowledgeBaseId, String fileHash);

    DocumentDeleteResultVO deleteById(Long documentId);
}
