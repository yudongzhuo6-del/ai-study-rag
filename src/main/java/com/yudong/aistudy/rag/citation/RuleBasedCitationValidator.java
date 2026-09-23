package com.yudong.aistudy.rag.citation;

import com.yudong.aistudy.model.vo.chat.ChatSourceVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedCitationValidator implements CitationValidator {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    @Override
    public CitationValidationResult validate(String answer, List<ChatSourceVO> sources) {
        CitationValidationResult result = new CitationValidationResult();
        Set<Integer> validIndexes = collectValidIndexes(sources);
        List<Integer> citedIndexes = extractCitedIndexes(answer);
        List<Integer> invalidIndexes = new ArrayList<>();

        for (Integer citedIndex : citedIndexes) {
            if (!validIndexes.contains(citedIndex)) {
                invalidIndexes.add(citedIndex);
            }
        }

        result.setCitedIndexes(citedIndexes);
        result.setInvalidIndexes(invalidIndexes);
        result.setMissingCitation(!validIndexes.isEmpty() && citedIndexes.isEmpty());
        result.setValid(!result.isMissingCitation() && invalidIndexes.isEmpty());
        return result;
    }

    private Set<Integer> collectValidIndexes(List<ChatSourceVO> sources) {
        Set<Integer> validIndexes = new HashSet<>();
        if (sources == null || sources.isEmpty()) {
            return validIndexes;
        }

        for (ChatSourceVO source : sources) {
            if (source != null && source.getCitationIndex() != null) {
                validIndexes.add(source.getCitationIndex());
            }
        }

        return validIndexes;
    }

    private List<Integer> extractCitedIndexes(String answer) {
        List<Integer> citedIndexes = new ArrayList<>();
        if (answer == null || answer.trim().isEmpty()) {
            return citedIndexes;
        }

        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            Integer citedIndex = parseInteger(matcher.group(1));
            if (citedIndex != null && !citedIndexes.contains(citedIndex)) {
                citedIndexes.add(citedIndex);
            }
        }

        return citedIndexes;
    }

    private Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
