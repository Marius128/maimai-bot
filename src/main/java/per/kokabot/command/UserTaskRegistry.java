package per.kokabot.command;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks long-running user tasks in this bot process.
 *
 * <p>The actual work is already executed by bounded asynchronous executors.
 * This registry prevents the same user from submitting the same expensive
 * operation repeatedly before the previous one has completed.</p>
 */
@Component
public class UserTaskRegistry {

    private final Set<TaskKey> runningTasks = ConcurrentHashMap.newKeySet();

    public boolean tryStart(long userId, String taskName) {
        return runningTasks.add(new TaskKey(userId, taskName));
    }

    public void finish(long userId, String taskName) {
        runningTasks.remove(new TaskKey(userId, taskName));
    }

    private record TaskKey(long userId, String taskName) {
    }
}
