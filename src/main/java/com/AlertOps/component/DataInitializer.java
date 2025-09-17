package com.AlertOps.component;

import com.AlertOps.model.Permission;
import com.AlertOps.model.Role;
import com.AlertOps.repository.PermissionRepository;
import com.AlertOps.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    public DataInitializer(RoleRepository roleRepo, PermissionRepository permissionRepo) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
    }

    @Override
    public void run(String... args) {
        System.out.println("⚡ Seeding roles & permissions...");

        Role userRole = roleRepo.findByName("USER");
        if (userRole == null) {
            userRole = new Role();
            userRole.setName("USER");
            Permission pr = new Permission();
            pr.setName("READ_TASK");
            pr.setDescription("This role will help user to read tasks");
            pr = permissionRepo.save(pr);
            userRole.getPermissions().add(pr);
            roleRepo.save(userRole);
        }
        System.out.println("✅ Seeding done.");
    }
}
