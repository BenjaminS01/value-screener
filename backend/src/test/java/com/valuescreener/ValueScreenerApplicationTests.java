package com.valuescreener;

import com.valuescreener.persistence.DynamoTestSupport;
import com.valuescreener.research.StockGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ValueScreenerApplicationTests extends DynamoTestSupport {

    @Autowired
    private StockGroupRepository stockGroups;

    @DynamicPropertySource
    static void adminCredentials(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.table-name", DynamoTestSupport::freshTableName);
        registry.add("app.admin.username", () -> "admin");
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder().encode("test-password"));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void startupCreatesTableThenSeedsStockGroups() {
        // seeding only works if the table initializer ran first (runner order)
        assertThat(stockGroups.findAll()).hasSize(35);
    }
}
