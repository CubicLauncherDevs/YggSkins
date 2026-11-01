package dev.yggskins.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UuidUtilTest {

    private static final String DASHED = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String UNDASHED = "069a79f444e94726a5befca90e38aaf5";

    @Test
    void parsesDashedAndUndashed() {
        UUID expected = UUID.fromString(DASHED);
        assertEquals(expected, UuidUtil.parse(DASHED));
        assertEquals(expected, UuidUtil.parse(UNDASHED));
        assertEquals(expected, UuidUtil.parse(DASHED.toUpperCase()));
    }

    @Test
    void rejectsInvalidValues() {
        assertNull(UuidUtil.parse("Notch"));
        assertNull(UuidUtil.parse(""));
        assertNull(UuidUtil.parse(null));
        assertNull(UuidUtil.parse("069a79f444e94726a5befca90e38aaf"));
    }

    @Test
    void formatsWithoutDashes() {
        assertEquals(UNDASHED, UuidUtil.undashed(UUID.fromString(DASHED)));
    }
}
