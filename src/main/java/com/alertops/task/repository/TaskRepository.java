
package com.alertops.task.repository;

// import com.alertops.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.alertops.task.interfaces.TaskView;
import com.alertops.task.model.Task;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, Long> {

    //find queries 
    @Query("SELECT t FROM Task t WHERE t.id = :taskId AND t.teamId = :teamId AND t.deleted = false")
    TaskView findById(UUID taskId, UUID teamId);
    
    Page<TaskView> findByTeamId(UUID teamId, Pageable pageable);

    // update queries
    @Modifying
    @Query("UPDATE Task t SET t.name = :name WHERE t.id = :id AND t.teamId = :teamId")
    void updateTaskName(@Param("id") UUID id,
                        @Param("name") String name,
                        @Param("teamId") UUID teamId
                    );

    @Modifying
    @Query("UPDATE Task t SET t.description = :description WHERE t.id = :id and t.teamId = :teamId")
    void updateTaskDescription(@Param("id") UUID id,
                               @Param("description") String description,
                                @Param("teamId") UUID teamId
                            );

    // soft-delete queries
    @Modifying
    @Query("UPDATE Task t SET t.deleted = true WHERE t.id = :id AND t.teamId = :teamId")
    void deleteTaskById(@Param("id") UUID id, @Param("teamId") UUID teamId);
}
