package com.alertops.repository;

import com.alertops.model.Permission;
import com.alertops.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Role findByName(String name);
}
