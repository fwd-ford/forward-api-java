package com.fwdford.forwardapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.repository.ScoreRepository;

class ScoreServiceTest {

    private static final String ID = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10";

    private ScoreRepository repo;
    private ScoreService service;

    @BeforeEach
    void setup() {
        repo = Mockito.mock(ScoreRepository.class);
        service = new ScoreService(repo);
        ChurnScore s = new ChurnScore(ID, ID, null, "v1", "high", 0.82, 0.9, OffsetDateTime.now());
        when(repo.findCurrentByCustomer(anyString())).thenReturn(Optional.of(s));
    }

    @Test
    void user_role_is_forbidden() {
        ApiException ex = assertThrows(ApiException.class, () -> service.getCurrent(ID, "user"));
        assertEquals("forbidden", ex.code());
    }

    @Test
    void missing_role_is_forbidden() {
        assertThrows(ApiException.class, () -> service.getCurrent(ID, null));
    }

    @Test
    void analyst_can_read_scores() {
        assertDoesNotThrow(() -> service.getCurrent(ID, "analyst"));
    }

    @Test
    void admin_can_read_scores() {
        assertDoesNotThrow(() -> service.getCurrent(ID, "admin"));
    }

    @Test
    void dealer_can_read_scores() {
        assertDoesNotThrow(() -> service.getCurrent(ID, "dealer"));
    }

    @Test
    void missing_score_yields_not_found() {
        when(repo.findCurrentByCustomer(anyString())).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.getCurrent(ID, "admin"));
        assertEquals("not_found", ex.code());
    }
}
