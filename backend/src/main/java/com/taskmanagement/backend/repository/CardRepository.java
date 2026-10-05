package com.taskmanagement.backend.repository;

import com.taskmanagement.backend.entity.Card;
import com.taskmanagement.backend.entity.Priority;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByColumnIdOrderByPosition(Long columnId);

    /** keyword は LIKE のワイルドカードを \ でエスケープ済みの値を渡すこと(null なら絞り込まない)。 */
    @EntityGraph(attributePaths = {"column", "labels"})
    @Query(
            """
            SELECT c FROM Card c
            WHERE c.column.user.id = :userId
              AND (:columnId IS NULL OR c.column.id = :columnId)
              AND (:priority IS NULL OR c.priority = :priority)
              AND (:keyword IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) ESCAPE '\\')
            ORDER BY c.column.id, c.position
            """)
    List<Card> search(
            @Param("userId") Long userId,
            @Param("columnId") Long columnId,
            @Param("priority") Priority priority,
            @Param("keyword") String keyword);

    @EntityGraph(attributePaths = {"column", "labels"})
    Optional<Card> findByIdAndColumnUserId(Long id, Long userId);

    @Query(
            """
            SELECT new com.taskmanagement.backend.repository.ColumnCardCount(c.column.id, COUNT(c))
            FROM Card c
            WHERE c.column.user.id = :userId
            GROUP BY c.column.id
            """)
    List<ColumnCardCount> countByColumnForUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(MAX(c.position), -1) FROM Card c WHERE c.column.id = :columnId")
    int maxPositionInColumn(@Param("columnId") Long columnId);
}
