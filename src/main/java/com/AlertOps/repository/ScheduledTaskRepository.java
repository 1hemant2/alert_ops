package com.AlertOps.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.AlertOps.model.ScheduledTask;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {


    List<ScheduledTask> findByQueuedFalseAndStatusIn(List<String> statuses);
    
    
    @Query("SELECT s FROM ScheduledTask s WHERE s.queued = false AND s.status IN :statuses")
    List<ScheduledTask> findPendingToPublish(@Param("statuses") List<String> statuses);
    
    
    List<ScheduledTask> findByStatusAndNextFireAtBefore(String status, Instant instant);
    
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ScheduledTask> findById(Long id);
}
