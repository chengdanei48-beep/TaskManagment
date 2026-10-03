package com.taskmanagement.backend.repository;

import com.taskmanagement.backend.entity.Card;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByColumnIdOrderByPosition(Long columnId);
}
