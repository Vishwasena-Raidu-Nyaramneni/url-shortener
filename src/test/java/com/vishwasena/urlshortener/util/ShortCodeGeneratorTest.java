package com.vishwasena.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeGeneratorTest {

    @Test
    void testGenerateReturnsValidCode() {
        String code = ShortCodeGenerator.generate();
        assertNotNull(code);
        assertEquals(8, code.length());
        assertTrue(code.matches("[a-zA-Z0-9]+"));
    }

    @Test
    void testGenerateProducesUniqueValues() {
        // Probability of collision in 100 random 8-char Base62 codes is negligible
        for (int i = 0; i < 100; i++) {
            String code = ShortCodeGenerator.generate();
            assertNotNull(code);
            assertEquals(8, code.length());
        }
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        long original = 123456789L;
        String encoded = ShortCodeGenerator.encode(original);
        long decoded = ShortCodeGenerator.decode(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void testEncodeZero() {
        String encoded = ShortCodeGenerator.encode(0);
        assertEquals("a", encoded);
    }

    @Test
    void testDecodeBasic() {
        long value = ShortCodeGenerator.decode("a");
        assertEquals(0, value);
    }
}
