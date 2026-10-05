package com.taskmanagement.backend.repository;

import com.taskmanagement.backend.entity.BoardColumn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByUserIdOrderByPosition(Long userId);

    List<BoardColumn> findAllByOrderByPosition();

    int countByUserId(Long userId);
}
