package com.taskmanagement.backend.repository;

import com.taskmanagement.backend.entity.BoardColumn;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByUserIdOrderByPosition(Long userId);

    Optional<BoardColumn> findByIdAndUserId(Long id, Long userId);

    int countByUserId(Long userId);
}
