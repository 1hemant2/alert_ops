package com.AlertOps.service;

import com.AlertOps.model.User;
import com.AlertOps.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user) {
        try {
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
}


