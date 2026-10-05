package com.taskmanagement.backend.service;

import com.taskmanagement.backend.dto.ColumnRequest;
import com.taskmanagement.backend.dto.ColumnResponse;
import com.taskmanagement.backend.dto.ColumnSortRequest.SortKey;
import com.taskmanagement.backend.entity.BoardColumn;
import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.exception.ResourceNotFoundException;
import com.taskmanagement.backend.repository.BoardColumnRepository;
import com.taskmanagement.backend.repository.CardRepository;
import com.taskmanagement.backend.repository.ColumnCardCount;
import com.taskmanagement.backend.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ColumnService {

    // Priority は HIGH, MEDIUM, LOW の宣言順 = 「重→中→低」。未設定(null)は最後
    private static final Comparator<Card> BY_PRIORITY =
            Comparator.comparing(
                    Card::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));
    // 期限が近い順。期限未設定(null)は最後
    private static final Comparator<Card> BY_DUE_DATE =
            Comparator.comparing(
                    Card::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));

    static final int COLUMN_LIMIT = 10;

    private final BoardColumnRepository boardColumnRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;

    public ColumnService(
            BoardColumnRepository boardColumnRepository,
            CardRepository cardRepository,
            UserRepository userRepository) {
        this.boardColumnRepository = boardColumnRepository;
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
    }

    public List<ColumnResponse> findAll(Long userId) {
        Map<Long, Long> counts = new HashMap<>();
        for (ColumnCardCount count : cardRepository.countByColumnForUser(userId)) {
            counts.put(count.columnId(), count.cardCount());
        }
        return boardColumnRepository.findByUserIdOrderByPosition(userId).stream()
                .map(column -> ColumnResponse.from(column, counts.getOrDefault(column.getId(), 0L)))
                .toList();
    }

    /** 列を末尾に追加する。列数は上限10(超えたら409)。列名の形式は DTO の Bean Validation で検証済み。 */
    @Transactional
    public ColumnResponse create(Long userId, ColumnRequest request) {
        int count = boardColumnRepository.countByUserId(userId);
        if (count >= COLUMN_LIMIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "列は最大" + COLUMN_LIMIT + "列までです");
        }
        BoardColumn column = new BoardColumn();
        column.setUser(userRepository.getReferenceById(userId));
        column.setName(request.name());
        column.setPosition(count);
        return ColumnResponse.from(boardColumnRepository.save(column), 0);
    }

    /**
     * 列と、その列のカードを削除する(カードはDBの ON DELETE CASCADE で消える)。 残りの列の position
     * は0からの連番に振り直す。列が存在しない・他人のものなら404。
     */
    @Transactional
    public void delete(Long userId, Long columnId) {
        BoardColumn column =
                boardColumnRepository
                        .findByIdAndUserId(columnId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("カラムが見つかりません"));
        boardColumnRepository.delete(column);
        boardColumnRepository.flush();
        List<BoardColumn> rest = boardColumnRepository.findByUserIdOrderByPosition(userId);
        for (int i = 0; i < rest.size(); i++) {
            rest.get(i).setPosition(i);
        }
    }

    /**
     * 列内のカードを指定の基準で並び替え、結果を position に保存する。 基準が同じカード同士は現在の並びを保つ(List.sort は安定ソート)。
     * 列が存在しない・他人のものなら404。
     */
    @Transactional
    public void sortCards(Long userId, Long columnId, SortKey by) {
        if (boardColumnRepository.findByIdAndUserId(columnId, userId).isEmpty()) {
            throw new ResourceNotFoundException("カラムが見つかりません");
        }
        List<Card> cards = new ArrayList<>(cardRepository.findByColumnIdOrderByPosition(columnId));
        cards.sort(by == SortKey.PRIORITY ? BY_PRIORITY : BY_DUE_DATE);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setPosition(i);
        }
    }
}
