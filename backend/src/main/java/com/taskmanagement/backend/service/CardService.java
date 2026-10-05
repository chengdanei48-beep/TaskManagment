package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.CardRequest;
import com.taskmanagement.backend.dto.CardResponse;
import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Priority;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.CardRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CardService {

    static final int TITLE_MAX = 50;
    static final int DESCRIPTION_MAX = 500;

    private final CardRepository cardRepository;
    private final BoardColumnRepository boardColumnRepository;

    public CardService(CardRepository cardRepository, BoardColumnRepository boardColumnRepository) {
        this.cardRepository = cardRepository;
        this.boardColumnRepository = boardColumnRepository;
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

    /** 指定カラムの末尾にカードを追加する。カラムが自分のものでなければ404。 */
    @Transactional
    public CardResponse create(Long userId, CardRequest request) {
        validate(request);
        if (request.columnId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "カラムを指定してください");
        }
        BoardColumn column = boardColumnRepository
                .findByIdAndUserId(request.columnId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "カラムが見つかりません"));

        Card card = new Card();
        card.setColumn(column);
        card.setPosition(cardRepository.maxPositionInColumn(column.getId()) + 1);
        apply(card, request);
        return CardResponse.from(cardRepository.save(card));
    }

    @Transactional
    public Optional<CardResponse> update(Long userId, Long id, CardRequest request) {
        validate(request);
        return cardRepository.findByIdAndColumnUserId(id, userId).map(card -> {
            apply(card, request);
            return CardResponse.from(card);
        });
    }

    /** 削除できたら true。存在しない・他人のカードなら false。 */
    @Transactional
    public boolean delete(Long userId, Long id) {
        return cardRepository.findByIdAndColumnUserId(id, userId)
                .map(card -> {
                    cardRepository.delete(card);
                    return true;
                })
                .orElse(false);
    }

    private static void apply(Card card, CardRequest request) {
        card.setTitle(request.title().trim());
        String description = request.description();
        card.setDescription(description == null || description.isBlank() ? null : description);
        card.setDueDate(request.dueDate());
        card.setPriority(request.priority());
    }

    private static void validate(CardRequest request) {
        String title = request.title();
        if (title == null || title.isBlank() || title.trim().length() > TITLE_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "タイトルは1〜" + TITLE_MAX + "文字で入力してください");
        }
        if (request.description() != null && request.description().length() > DESCRIPTION_MAX) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "詳細説明は" + DESCRIPTION_MAX + "文字までで入力してください");
        }
    }
}
