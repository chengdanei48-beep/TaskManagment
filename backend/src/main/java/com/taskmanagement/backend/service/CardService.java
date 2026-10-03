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

    public List<CardResponse> search(Long columnId, Priority priority, String keyword) {
        return cardRepository.search(columnId, priority, keyword).stream()
                .map(CardResponse::from)
                .toList();
    }

    public Optional<CardResponse> findById(Long id) {
        return cardRepository.findById(id).map(CardResponse::from);
    }
}
