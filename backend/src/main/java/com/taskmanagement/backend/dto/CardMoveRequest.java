package com.taskmanagement.backend.dto;

/** カードの移動先。beforeCardId のカードの手前に挿入し、null なら移動先の列の末尾に置く。 */
public record CardMoveRequest(Long columnId, Long beforeCardId) {}
