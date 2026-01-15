
package com.alertops.task.controller;

import com.alertops.task.model.Task;
import com.alertops.task.service.TaskService;
import com.alertops.task.task.CreateTask;
import com.alertops.task.task.UpdateTaskRequestDTO;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/v1/task")
public class TaskController {
    private final TaskService taskService;

    public  TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody CreateTask req) {
            String name = req.getName();
            String descripton = req.getDescription();
            Task savedTask = taskService.createTask(name, descripton);
            return ResponseEntity.ok("task created with id: " + savedTask.getId());
    }

    @GetMapping("{taskId}")
    public ResponseEntity<?> getTaskById(@PathVariable UUID taskId) {
        return  ResponseEntity.ok(taskService.getTaskById(taskId));
    }

    @PutMapping("/name")
    public ResponseEntity<?> updateTaskByName(@RequestBody UpdateTaskRequestDTO req) {
        return  ResponseEntity.ok(taskService.updateTaskName(req.getId(), req.getName()));
    }

    @PutMapping("/description")
    public ResponseEntity<?> updateTaskDescription(@RequestBody UpdateTaskRequestDTO req) {
        return  ResponseEntity.ok(taskService.updateTaskDescription(req.getId(), req.getDescription()));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteTaskById(@RequestParam UUID taskId) {
        return  ResponseEntity.ok(taskService.deleteTaskById(taskId));
    }

    @GetMapping
    public ResponseEntity<?> getTasksByTeamId(@RequestParam int page, @RequestParam int size, @RequestParam String sortBy, @RequestParam String sortDir) {
        return ResponseEntity.ok(taskService.getTasksByTeamId(page, size, sortBy, sortDir));
    }
}

