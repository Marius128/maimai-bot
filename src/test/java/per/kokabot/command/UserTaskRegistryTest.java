package per.kokabot.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTaskRegistryTest {

    @Test
    void rejectsDuplicateTaskUntilPreviousTaskFinishes() {
        UserTaskRegistry registry = new UserTaskRegistry();

        assertTrue(registry.tryStart(10001L, "b50"));
        assertFalse(registry.tryStart(10001L, "b50"));

        registry.finish(10001L, "b50");

        assertTrue(registry.tryStart(10001L, "b50"));
    }

    @Test
    void allowsDifferentUsersAndDifferentTaskTypes() {
        UserTaskRegistry registry = new UserTaskRegistry();

        assertTrue(registry.tryStart(10001L, "b50"));
        assertTrue(registry.tryStart(10002L, "b50"));
        assertTrue(registry.tryStart(10001L, "update"));
    }
}
