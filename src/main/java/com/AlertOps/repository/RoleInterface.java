package com.AlertOps.repository;

import com.AlertOps.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleInterface extends JpaRepository<Role, Long> {
    
}
