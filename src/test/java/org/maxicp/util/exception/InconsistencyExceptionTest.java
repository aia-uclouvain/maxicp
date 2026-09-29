package org.maxicp.util.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InconsistencyExceptionTest {

    @Test
    void singletonStackTraceFollowsEnvironmentSetting() {
        boolean stackTraceEnabled = Boolean.parseBoolean(System.getenv("MAXICP_INCONSISTENCY_STACKTRACE"));

        InconsistencyException first = InconsistencyException.INCONSISTENCY();
        InconsistencyException second = InconsistencyException.INCONSISTENCY();

        assertEquals(stackTraceEnabled, first.getStackTrace().length > 0);
        assertEquals(!stackTraceEnabled, first == second);
    }

    @Test
    void methodCapturesStackTraceAtEachCallWhenEnabled() {
        assumeTrue(Boolean.parseBoolean(System.getenv("MAXICP_INCONSISTENCY_STACKTRACE")));

        InconsistencyException first = InconsistencyException.INCONSISTENCY();
        InconsistencyException second = InconsistencyException.INCONSISTENCY();

        assertNotSame(first, second);
        assertTrue(second.getStackTrace().length > 0);
    }

    @Test
    void singletonStackTraceReflectsLatestThrowWhenEnabled() {
        assumeTrue(Boolean.parseBoolean(System.getenv("MAXICP_INCONSISTENCY_STACKTRACE")));

        try {
            throwFromFirstMethod();
        } catch (InconsistencyException ignored) {
        }

        try {
            throwFromSecondMethod();
        } catch (InconsistencyException exception) {
            assertTrue(java.util.Arrays.stream(exception.getStackTrace())
                    .anyMatch(frame -> frame.getMethodName().equals("throwFromSecondMethod")));
        }
    }

    private void throwFromFirstMethod() {
        throw InconsistencyException.INCONSISTENCY();
    }

    private void throwFromSecondMethod() {
        throw InconsistencyException.INCONSISTENCY();
    }
}