package com.AlertOps.repository;

import com.AlertOps.model.Permission;
import com.AlertOps.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Role findByName(String name);
}
