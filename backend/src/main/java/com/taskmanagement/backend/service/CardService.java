package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.repository.CardRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public List<CardResponse> search(Long userId, Long columnId, Priority priority, String keyword) {
        return cardRepository.search(userId, columnId, priority, keyword).stream()
                .map(CardResponse::from)
                .toList();
    }

    /** 他の利用者のカードは存在しないものとして扱う。 */
    public Optional<CardResponse> findById(Long userId, Long id) {
        return cardRepository.findByIdAndColumnUserId(id, userId).map(CardResponse::from);
    }
}
