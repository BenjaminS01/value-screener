package com.valuescreener.deepanalysis;

import com.valuescreener.limits.JobType;
import com.valuescreener.limits.Quota;
import com.valuescreener.limits.UsageLimiter;
import com.valuescreener.research.CompanySnapshotNotFoundException;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.security.Role;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/research")
public class DeepAnalysisController {

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final UsageLimiter usageLimiter;
    private final DeepAnalysisInvoker invoker;
    private final CompanySnapshotService snapshotService;

    public DeepAnalysisController(UsageLimiter usageLimiter, DeepAnalysisInvoker invoker,
                                  CompanySnapshotService snapshotService) {
        this.usageLimiter = usageLimiter;
        this.invoker = invoker;
        this.snapshotService = snapshotService;
    }

    /** Order matters: snapshot exists (404, no quota) -> consume quota (429) -> invoke (502, no refund). */
    @PostMapping("/snapshots/{isin}/deep-analysis")
    public DeepAnalysisResponse run(@PathVariable String isin, Authentication authentication) {
        String normalized = isin.trim().toUpperCase(Locale.ROOT);
        if (!ISIN.matcher(normalized).matches()) {
            throw new CompanySnapshotNotFoundException(isin);
        }
        snapshotService.getByIsin(normalized);
        Quota quota = usageLimiter.consume(authentication.getName(), roleOf(authentication),
                JobType.DEEP_ANALYSIS_DEMO);
        return DeepAnalysisResponse.of(invoker.invoke(normalized), quota);
    }

    @GetMapping("/deep-analysis/quota")
    public Quota quota(Authentication authentication) {
        return usageLimiter.quota(authentication.getName(), roleOf(authentication), JobType.DEEP_ANALYSIS_DEMO);
    }

    private static Role roleOf(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.ADMIN.name()));
        return admin ? Role.ADMIN : Role.USER;
    }
}
