package ru.privatenull.pnrelics.integration;

import org.pnfolder.pnlibrary.api.tasks.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.TaskSpec;

/**
 * Единственное место, где pnRelics запускает задачи: планировщик pnLibrary ({@code ModuleContext.tasks}).
 * Задачи выполняются в глобальном потоке сервера и отменяются вместе с модулем при отключении плагина.
 */
public final class PluginTasks {

    private static volatile TaskScope scope;

    private PluginTasks() {
    }

    static void bind(TaskScope tasks) {
        scope = tasks;
    }

    private static TaskHandle schedule(long delayTicks, long periodTicks, Runnable action) {
        TaskScope tasks = scope;
        if (tasks == null) throw new IllegalStateException("Планировщик pnLibrary недоступен");
        TaskSpec.Builder spec = TaskSpec.builder()
                .name("pnrelics")
                .execution(TaskExecution.global())
                .delayTicks(delayTicks)
                .action(context -> action.run());
        if (periodTicks > 0L) spec.intervalTicks(periodTicks);
        return tasks.schedule(spec.build());
    }

    /** Запускает {@code action} в потоке сервера на следующем тике. */
    public static TaskHandle run(Runnable action) {
        return schedule(1L, 0L, action);
    }

    /** Повторяет {@code action} каждые {@code periodTicks} тиков после задержки {@code delayTicks}. */
    public static TaskHandle timer(long delayTicks, long periodTicks, Runnable action) {
        return schedule(delayTicks, periodTicks, action);
    }
}
