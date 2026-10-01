package com.alertops.task.service;

import com.alertops.exception.TaskException;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.interfaces.TaskView;
import com.alertops.task.model.Task;
import com.alertops.task.repository.TaskRepository;
import com.alertops.task.task.TaskResponseDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;


@Service
public class TaskService {
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }


    @Transactional
    public Task createTask(String name, String description, String source, String priority,
                           String category, String referenceUrl) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            if(authContext.getTeamId() == null) {
                throw TaskException.creationFailed(new RuntimeException("User not part of any team"));
            }
            validateTaskFields(name, source, priority, category, referenceUrl);
            Task task = new Task();
            task.setName(name);
            task.setDescription(description);
            task.setSource(blank(source) ? "Manual" : source.trim());
            task.setPriority(trimToNull(priority));
            task.setCategory(trimToNull(category));
            task.setReferenceUrl(trimToNull(referenceUrl));
            task.setTeamId(authContext.getTeamId());
            Task savedTask = taskRepository.save(task);
            return  savedTask;
        } catch (Exception e) {
            throw  TaskException.creationFailed(e);
        }
    }

    public  TaskResponseDto getTaskById(UUID taskId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            TaskView task = taskRepository.findById(taskId, authContext.getTeamId());
            if(task == null) {
                return null;
            }
            return new TaskResponseDto(
                    task.getId(),
                    task.getName(),
                    task.getDescription(),
                    task.getSource(),
                    task.getPriority(),
                    task.getCategory(),
                    task.getReferenceUrl()
            );
        } catch(Exception e) {
            throw  TaskException.getFailed(e);
        }
    }

    public List<TaskView> getTasksByTeamId(int page, int size, String sortBy, String sortDir) { 
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            Set<String> allowedSortBy = Set.of("taskName", "createdAt");
            Set<String> allowedSortDir = Set.of("asc", "desc");

            if(page < 0) {
                page = 0;
            }

            if(!allowedSortBy.contains(sortBy)) {
                sortBy = "createdAt";
            }

            if(!allowedSortDir.contains(sortDir)) {
                sortDir = "asc";
            }

            Sort sort = Sort.by(Sort.Direction.valueOf(sortDir.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<TaskView> taskPage = taskRepository.findByTeamId(teamId, pageable);
            List<TaskView> tasks = taskPage.getContent();
            return tasks;
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    @Transactional
    public TaskResponseDto updateTaskName(UUID taskId, String updatedName) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            taskRepository.updateTaskName(taskId, updatedName, teamId);
            return  getTaskById(taskId);
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    @Transactional
    public TaskResponseDto updateTaskDescription(UUID taskId, String updatedDescription) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            taskRepository.updateTaskDescription(taskId, updatedDescription, teamId);
            return  getTaskById(taskId);
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    @Transactional
    public TaskResponseDto updateTaskDetails(UUID taskId, String source, String priority,
                                             String category, String referenceUrl) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            validateTaskFields("valid", source, priority, category, referenceUrl);
            taskRepository.updateTaskDetails(taskId, blank(source) ? "Manual" : source.trim(),
                    trimToNull(priority), trimToNull(category), trimToNull(referenceUrl),
                    authContext.getTeamId());
            return getTaskById(taskId);
        } catch (Exception e) {
            throw TaskException.getFailed(e);
        }
    }

    private void validateTaskFields(String name, String source, String priority, String category,
                                    String referenceUrl) {
        if (blank(name) || name.trim().length() > 120) {
            throw new IllegalArgumentException("Task title is required and must be 120 characters or fewer");
        }
        if (!blank(source) && source.trim().length() > 120) {
            throw new IllegalArgumentException("Source must be 120 characters or fewer");
        }
        if (!blank(category) && category.trim().length() > 80) {
            throw new IllegalArgumentException("Category must be 80 characters or fewer");
        }
        if (!blank(referenceUrl) && (referenceUrl.trim().length() > 2048
                || !HTTP_URL.matcher(referenceUrl.trim()).matches())) {
            throw new IllegalArgumentException("Reference URL must be a valid HTTP(S) URL");
        }
    }

    private String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public String deleteTaskById(UUID taskId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            taskRepository.deleteTaskById(taskId, teamId);
            return  "task deleted successfully with taskId: ";
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

}
