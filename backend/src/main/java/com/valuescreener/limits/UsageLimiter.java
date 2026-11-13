package com.valuescreener.limits;

import com.valuescreener.security.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Daily usage limits counted in Postgres with one atomic upsert per row. */
@Service
@EnableConfigurationProperties(LimitsProperties.class)
public class UsageLimiter {

    static final String GLOBAL_ACCOUNT = "*";
    static final String GLOBAL_JOB_TYPE = "ALL";

    private static final String UPSERT = "INSERT INTO usage_counter(account, job_type, day, count) "
            + "VALUES (?, ?, ?, 1) ON CONFLICT (account, job_type, day) "
            + "DO UPDATE SET count = usage_counter.count + 1 WHERE usage_counter.count < ? "
            + "RETURNING count";

    private final JdbcTemplate jdbc;
    private final LimitsProperties props;
    private final Clock clock;

    public UsageLimiter(JdbcTemplate jdbc, LimitsProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Quota consume(String account, Role role, JobType type) {
        String acct = account.toLowerCase(Locale.ROOT);
        int limit = limitFor(role);
        LocalDate day = LocalDate.now(clock);

        Integer used = increment(acct, type.name(), day, limit);
        if (used == null) {
            throw new LimitReachedException(LimitReachedException.Scope.ACCOUNT);
        }
        if (increment(GLOBAL_ACCOUNT, GLOBAL_JOB_TYPE, day, props.globalPerDay()) == null) {
            // RuntimeException: Spring rolls back the account increment above.
            throw new LimitReachedException(LimitReachedException.Scope.GLOBAL);
        }
        return new Quota(Math.max(0, limit - used), limit);
    }

    @Transactional(readOnly = true)
    public Quota quota(String account, Role role, JobType type) {
        int limit = limitFor(role);
        List<Integer> rows = jdbc.queryForList(
                "SELECT count FROM usage_counter WHERE account = ? AND job_type = ? AND day = ?",
                Integer.class, account.toLowerCase(Locale.ROOT), type.name(), LocalDate.now(clock));
        int used = rows.isEmpty() ? 0 : rows.get(0);
        return new Quota(Math.max(0, limit - used), limit);
    }

    private int limitFor(Role role) {
        return role == Role.ADMIN ? props.deepAnalysisPerAdmin() : props.deepAnalysisPerUser();
    }

    /** Returns the new count, or null when the limit is reached (no row written). */
    private Integer increment(String account, String jobType, LocalDate day, int limit) {
        if (limit <= 0) {
            return null;
        }
        List<Integer> rows = jdbc.queryForList(UPSERT, Integer.class, account, jobType, day, limit);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
