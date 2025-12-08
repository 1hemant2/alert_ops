package com.alertops.repository;

import  com.alertops.model.User;
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
    User findByUserName(String userName);

    //Delete queries
    void deleteByUserName(String userName);
    void deleteByEmail(String email);


    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.email = :newEmail WHERE u.email = :oldEmail")
    int updateUserEmailByEmail(String oldEmail, String newEmail);

    //List<escalation> findByOwnerIdOrderByStartTimeAsc(Long ownerId);


    @Query("select u from User u left join fetch u.escalations where u.id = :userId")
    Optional<User> findByIdWithEscalations(@Param("userId") Long userId);

}
