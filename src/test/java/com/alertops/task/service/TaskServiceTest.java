package com.alertops.task.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alertops.exception.TaskException;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.model.Task;
import com.alertops.task.repository.TaskRepository;

class TaskServiceTest {
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskService taskService = new TaskService(taskRepository);
    private final UUID teamId = UUID.randomUUID();

    // Provides the team context required by manual task operations.
    @BeforeEach
    void setUp() {
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "USER", "token", "user@example.com"));
    }

    // Clears the thread-local context after each task validation test.
    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    // Rejects an oversized description before the task repository is called.
    @Test
    void rejectsOversizedDescriptionDuringCreate() {
        String description = "x".repeat(TaskService.MAX_TASK_DESCRIPTION_LENGTH + 1);

        assertThrows(TaskException.class,
                () -> taskService.createTask("Task", description, "Manual", "P1", "Incident", null));

        verify(taskRepository, never()).save(any(Task.class));
    }

    // Rejects an oversized priority before manual task creation persists data.
    @Test
    void rejectsOversizedPriorityDuringCreate() {
        String priority = "x".repeat(TaskService.MAX_TASK_PRIORITY_LENGTH + 1);

        assertThrows(TaskException.class,
                () -> taskService.createTask("Task", "Description", "Manual", priority, "Incident", null));

        verify(taskRepository, never()).save(any(Task.class));
    }

    // Rejects an oversized description before a manual update query executes.
    @Test
    void rejectsOversizedDescriptionDuringUpdate() {
        String description = "x".repeat(TaskService.MAX_TASK_DESCRIPTION_LENGTH + 1);

        assertThrows(TaskException.class,
                () -> taskService.updateTaskDescription(UUID.randomUUID(), description));

        verify(taskRepository, never()).updateTaskDescription(any(), any(), any());
    }
}
