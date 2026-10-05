package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.ColumnResponse;
import com.taskmanagement.backend.dto.ColumnSortRequest.SortKey;
import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.CardRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ColumnService {

    // Priority は HIGH, MEDIUM, LOW の宣言順 = 「重→中→低」。未設定(null)は最後
    private static final Comparator<Card> BY_PRIORITY =
            Comparator.comparing(Card::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));
    // 期限が近い順。期限未設定(null)は最後
    private static final Comparator<Card> BY_DUE_DATE =
            Comparator.comparing(Card::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));

    private final BoardColumnRepository boardColumnRepository;
    private final CardRepository cardRepository;

    public ColumnService(BoardColumnRepository boardColumnRepository, CardRepository cardRepository) {
        this.boardColumnRepository = boardColumnRepository;
        this.cardRepository = cardRepository;
    }

    public List<ColumnResponse> findAll(Long userId) {
        return boardColumnRepository.findByUserIdOrderByPosition(userId).stream()
                .map(ColumnResponse::from)
                .toList();
    }

    /**
     * 列内のカードを指定の基準で並び替え、結果を position に保存する。
     * 基準が同じカード同士は現在の並びを保つ(List.sort は安定ソート)。
     * 列が存在しない・他人のものなら false。
     */
    @Transactional
    public boolean sortCards(Long userId, Long columnId, SortKey by) {
        if (by == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "並び替えの基準を指定してください");
        }
        if (boardColumnRepository.findByIdAndUserId(columnId, userId).isEmpty()) {
            return false;
        }
        List<Card> cards = new ArrayList<>(cardRepository.findByColumnIdOrderByPosition(columnId));
        cards.sort(by == SortKey.PRIORITY ? BY_PRIORITY : BY_DUE_DATE);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setPosition(i);
        }
        return true;
    }
}
