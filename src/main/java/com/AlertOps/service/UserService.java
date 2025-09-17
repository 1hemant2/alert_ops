package com.AlertOps.service;

import com.AlertOps.model.Permission;
import com.AlertOps.model.Role;
import com.AlertOps.model.User;
import com.AlertOps.repository.RoleRepository;
import com.AlertOps.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private  final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


    public User loadUserByUsername(String username) {
        return userRepository.findByUserName(username);
               // .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

   public Set<String> getUserPermissions(String username) {
        User user = loadUserByUsername(username);
        System.out.println("user + ->> " + user.getRoles());
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }


    public User createUser(User user) {
        try {
            Role role = roleRepository.findByName("USER");
            user.getRoles().add(role);
            User saved = userRepository.save(user);
            log.info("✅ User created successfully: {}", saved);
            return saved;
        } catch (Exception e) {
            log.error("❌ Error while creating user: {}", user, e);
            throw new RuntimeException("Failed to create user", e);
        }
    }

    public List<User> getAllUser() {
        try {
            List<User> users = userRepository.findAll();
            log.info("✅ ser fetched successfully");
            return  users;
        } catch (Exception e) {
            log.error("❌ Error while Fetching all user", e);
            throw new RuntimeException("Failed to fetch all user", e);
        }
    }

    public User getUser(Long id, String userName, String email) {
        try {
            if(id != null) {// findById returns Optional<User>
                return userRepository.findById(id)
                        .orElse(null); // return null if not found
            }
            if(userName != null) {
                return userRepository.findByUserName(userName);
            }
            if(email != null) {
                return  userRepository.findByEmail(email);
            }
            return  null;
        } catch (Exception e) {
            log.error("❌ Error while Fetching user user", e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    public boolean deleteUser(Long id, String userName, String email) {
        try {
            boolean delted = false;
            if(id != null) {// findById returns Optional<User>
                userRepository.deleteById(id); // return null if not found
                delted = true;
            }
            if(userName != null) {
                userRepository.deleteByUserName(userName);
                delted = true;
            }
            if(email != null) {
               userRepository.deleteByEmail(email);
                delted = true;
            }
            return delted;
        } catch (Exception e) {
            log.error("❌ Error while Fetching user user", e);
            throw new RuntimeException("Failed to fetch user", e);
       }
    }

    public void updateUserEmail(String email, String newEmail) {
        try {
            if(email != null) {
                int updatedRows = userRepository.updateUserEmailByEmail(email, newEmail);
                log.info("number of row update date successfully: {}", updatedRows);
            }
        } catch (Exception e) {
            log.error("❌ Error while Fetching user user", e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    public  Set<Role> getUserRole(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow();
            return user.getRoles();
        } catch (RuntimeException e) {
            log.error("❌ Error while Fetching user role", e);
            throw new RuntimeException("Failed to fetch user role", e);
        }
    }

    public void addUserRole(Long userId, String newRole) {
        try{
            Role role = roleRepository.findByName(newRole);
            userRepository.findById(userId).ifPresent(u -> {
                u.getRoles().add(role);
                userRepository.save(u);
            });
        } catch (RuntimeException e) {
            log.error("❌ Error while updating user role", e);
            throw new RuntimeException("Failed to update user", e);
        }
    }

    public void updateUserRole(Long userId, String existingRole, String newRole) {
        try{
            Role role = roleRepository.findByName(existingRole);
            userRepository.findById(userId).ifPresent(u -> {
                u.getRoles().remove(role);
                u.getRoles().add(roleRepository.findByName(newRole));
                userRepository.save(u);
            });
        } catch (RuntimeException e) {
            log.error("❌ Error while updating user role", e);
            throw new RuntimeException("Failed to update user", e);
        }
    }

    public void deleteUserRole(Long userId, String roleName) {
        try{
            Role role = roleRepository.findByName(roleName);
            userRepository.findById(userId).ifPresent(u -> {
                u.getRoles().remove(role);
                userRepository.save(u);
            });
        } catch (RuntimeException e) {
            log.error("❌ Error while updating user role", e);
            throw new RuntimeException("Failed to update user", e);
        }
    }


}


