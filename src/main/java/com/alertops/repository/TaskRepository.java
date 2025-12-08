package com.alertops.repository;

import com.alertops.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // find queries
    List<Task> findByUser_email(String email);
    List<Task> findByUser_userName(String userName);

    // update queries
    @Modifying
    @Query("UPDATE Task t SET t.name = :name WHERE t.id = :id")
    void updateTaskName(@Param("id") Long id,
                        @Param("name") String name);

    @Modifying
    @Query("UPDATE Task t SET t.description = :description WHERE t.id = :id")
    void updateTaskDescription(@Param("id") Long id,
                               @Param("description") String description);

    // soft-delete queries
    @Modifying
    @Query("UPDATE Task t SET t.deleted = true WHERE t.id = :id")
    void deleteTaskById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Task t SET t.deleted = true WHERE t.user.id = :userId")
    void deleteUserTask(@Param("userId") Long userId);
}
