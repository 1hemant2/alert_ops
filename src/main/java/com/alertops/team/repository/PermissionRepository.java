package com.alertops.team.repository;

import com.alertops.team.model.Permission;
import com.alertops.team.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Role findByName(String name);
}
