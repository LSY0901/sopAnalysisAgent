package org.example.sopanalysisagent.util;

import org.example.sopanalysisagent.model.dto.RagResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextFormatterTest {

    @Test
    void emptyReturnsEmpty() {
        assertEquals("", RagContextFormatter.format(List.of()));
    }

    @Test
    void formatsSourceAndContent() {
        RagResult r = new RagResult();
        r.setSource("sop-1.pdf");
        r.setContent("先断电");
        r.setScore(0.9);
        String text = RagContextFormatter.format(List.of(r));
        assertTrue(text.contains("sop-1.pdf"));
        assertTrue(text.contains("先断电"));
    }
}
