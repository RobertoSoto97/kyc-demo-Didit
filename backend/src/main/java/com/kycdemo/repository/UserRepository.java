package com.kycdemo.repository;

import com.kycdemo.model.User;
import com.kycdemo.model.User.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByDiditSessionId(String diditSessionId);
    boolean existsByEmail(String email);
    List<User> findByKycStatusAndAdminFalse(KycStatus status);
    List<User> findByAdminFalse();
}
