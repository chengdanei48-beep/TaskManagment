package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.ColumnResponse;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ColumnService {

    private final BoardColumnRepository boardColumnRepository;

    public ColumnService(BoardColumnRepository boardColumnRepository) {
        this.boardColumnRepository = boardColumnRepository;
    }

    public List<ColumnResponse> findAll(Long userId) {
        return boardColumnRepository.findByUserIdOrderByPosition(userId).stream()
                .map(ColumnResponse::from)
                .toList();
    }
}
