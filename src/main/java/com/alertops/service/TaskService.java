package com.alertops.service;

import com.alertops.dto.task.TaskResponseDto;
import com.alertops.dto.task.UserTasksResponseDto;
import com.alertops.exception.TaskException;
import com.alertops.model.Task;
import com.alertops.model.User;
import com.alertops.repository.TaskRepository;
import com.alertops.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

//@Slf4j
@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public Task createTask(Long userId, String name, String description) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Task task = new Task();
            task.setUser(user);
            task.setName(name);
            task.setDescription(description);
            Task savedTask = taskRepository.save(task);
            return  savedTask;
        } catch (Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.creationFailed(e);
        }
    }

    public  TaskResponseDto getTaskByTaskId(Long taskId) {
        try {
            Task task = taskRepository.findById(taskId).orElse(null);
            if(task == null) {
                return null;
            }
            return new TaskResponseDto(task.getId(), task.getName(), task.getDescription(), task.getUser().getId());
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    public  List<UserTasksResponseDto> getTaskByUserName(String userName) {
        try {
            List<Task> task = taskRepository.findByUser_userName(userName);
            //Loop: “I go step by step and check each element.”
           // Stream: “Take all elements, give me the ones I care about, transform them, collect the result.”
            List<UserTasksResponseDto> tasksDto = task.stream()
                    .map(t -> new UserTasksResponseDto(
                            t.getId(),
                            t.getName(),
                            t.getDescription()
                    ))
                    .toList();
            return  tasksDto;
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    @Transactional
    public TaskResponseDto updateTaskName(Long taskId, String updatedName) {
        try {
            taskRepository.updateTaskName(taskId, updatedName);
            return  getTaskByTaskId(taskId);
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    @Transactional
    public TaskResponseDto updateTaskDescription(Long taskId, String updatedDescription) {
        try {
            taskRepository.updateTaskDescription(taskId, updatedDescription);
            return  getTaskByTaskId(taskId);
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }

    @Transactional
    public String deleteTaskById(Long taskId) {
        try {
            taskRepository.deleteTaskById(taskId);
            return  "task deleted successfully with taskId: ";
        } catch(Exception e) {
            // log.error("❌ Error while creating task: {}", e);
            throw  TaskException.getFailed(e);
        }
    }



}
