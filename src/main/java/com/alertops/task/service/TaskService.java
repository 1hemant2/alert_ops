package com.alertops.task.service;

import com.alertops.dto.task.TaskResponseDto;
import com.alertops.exception.TaskException;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.interfaces.TaskView;
import com.alertops.task.model.Task;
import com.alertops.task.repository.TaskRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;


//@Slf4j
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }


    @Transactional
    public Task createTask(String name, String description) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            if(authContext.getTeamId() == null) {
                throw TaskException.creationFailed(new RuntimeException("User not part of any team"));
            }
            Task task = new Task();
            task.setName(name);
            task.setDescription(description);
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
                    task.getDescription()
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
