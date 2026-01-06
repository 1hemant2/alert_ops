package com.alertops.auth.repository;

import com.alertops.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    //find queries
    User findByEmail(String email);

    //Delete queries
    void deleteByEmail(String email);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.email = :newEmail WHERE u.email = :oldEmail")
    int updateUserEmailByEmail(String oldEmail, String newEmail);

}
