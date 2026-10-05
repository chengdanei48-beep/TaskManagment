package com.taskmanagement.backend.repository;

import com.taskmanagement.backend.entity.Label;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, Long> {

    List<Label> findByUserIdOrderById(Long userId);

    Optional<Label> findByIdAndUserId(Long id, Long userId);

    List<Label> findByIdInAndUserId(Collection<Long> ids, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
