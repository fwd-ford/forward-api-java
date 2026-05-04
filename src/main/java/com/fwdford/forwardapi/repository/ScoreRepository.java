// Churn score repository. Returns the latest current score for a customer.
// Repositorio de scores: retorna o score atual mais recente do cliente.
package com.fwdford.forwardapi.repository;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fwdford.forwardapi.model.ChurnScore;

@Repository
public class ScoreRepository {

    private static final String SELECT_CURRENT = """
            SELECT id::text, customer_id::text, vin, model_version, segment,
                   churn_probability, confidence, computed_at
            FROM churn_scores
            WHERE customer_id = :customerId AND is_current = TRUE
            ORDER BY computed_at DESC
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ScoreRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ChurnScore> findCurrentByCustomer(String customerId) {
        var params = new MapSqlParameterSource("customerId", java.util.UUID.fromString(customerId));
        return jdbc.query(SELECT_CURRENT, params, (rs, idx) -> new ChurnScore(
                rs.getString("id"),
                rs.getString("customer_id"),
                rs.getString("vin"),
                rs.getString("model_version"),
                rs.getString("segment"),
                rs.getDouble("churn_probability"),
                (Double) rs.getObject("confidence"),
                rs.getObject("computed_at", java.time.OffsetDateTime.class)
        )).stream().findFirst();
    }
}
