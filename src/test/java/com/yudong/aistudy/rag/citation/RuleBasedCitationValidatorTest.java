package com.yudong.aistudy.rag.citation;

import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedCitationValidatorTest {

    private final RuleBasedCitationValidator validator = new RuleBasedCitationValidator();

    @Test
    void validateReturnsValidWhenCitationsMatchSources() {
        CitationValidationResult result = validator.validate("answer [1][2]", List.of(source(1), source(2)));

        assertTrue(result.isValid());
        assertFalse(result.isMissingCitation());
        assertIterableEquals(List.of(1, 2), result.getCitedIndexes());
        assertTrue(result.getInvalidIndexes().isEmpty());
    }

    @Test
    void validateDetectsMissingCitationWhenSourcesExist() {
        CitationValidationResult result = validator.validate("answer without citation", List.of(source(1)));

        assertFalse(result.isValid());
        assertTrue(result.isMissingCitation());
        assertTrue(result.getInvalidIndexes().isEmpty());
    }

    @Test
    void validateDetectsInvalidCitationIndex() {
        CitationValidationResult result = validator.validate("answer [1][3]", List.of(source(1), source(2)));

        assertFalse(result.isValid());
        assertFalse(result.isMissingCitation());
        assertIterableEquals(List.of(3), result.getInvalidIndexes());
    }

    @Test
    void validateDeduplicatesRepeatedCitationIndexes() {
        CitationValidationResult result = validator.validate("answer [1][1]", List.of(source(1)));

        assertTrue(result.isValid());
        assertIterableEquals(List.of(1), result.getCitedIndexes());
    }

    private ChatSourceVO source(Integer citationIndex) {
        ChatSourceVO source = new ChatSourceVO();
        source.setCitationIndex(citationIndex);
        return source;
    }
}
