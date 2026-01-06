/*
package com.alertops.controller;

import com.alertops.auth.controller.UserController;
import com.alertops.dto.task.CreateTask;
import com.alertops.dto.task.UpdateTaskRequestDTO;
import com.alertops.model.Task;
import com.alertops.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/v1")
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final TaskService taskService;

    public  TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/task")
    public ResponseEntity<?> createTask(@RequestBody CreateTask req) {
            Long id = req.getUserId();
            String name = req.getName();
            String descripton = req.getDescription();
            Task savedTask = taskService.createTask(id, name, descripton);
            return ResponseEntity.ok("task created with id: " + savedTask.getId());
    }

    @GetMapping("/task")
    public ResponseEntity<?> getTaskByTaskId(@RequestParam Long taskId) {
        return  ResponseEntity.ok(taskService.getTaskByTaskId(taskId));
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTaskByUserName(@RequestParam String userName) {
        return  ResponseEntity.ok(taskService.getTaskByUserName(userName));
    }

    @PutMapping("/task/name")
    public ResponseEntity<?> updateTaskByName(@RequestBody UpdateTaskRequestDTO req) {
        return  ResponseEntity.ok(taskService.updateTaskName(req.getId(), req.getName()));
    }

    @PutMapping("/task/description")
    public ResponseEntity<?> updateTaskDescription(@RequestBody UpdateTaskRequestDTO req) {
        return  ResponseEntity.ok(taskService.updateTaskDescription(req.getId(), req.getDescription()));
    }

    @DeleteMapping("/task")
    public ResponseEntity<?> deleteTaskById(@RequestParam Long taskId) {
        return  ResponseEntity.ok(taskService.deleteTaskById(taskId));
    }

}
*/
