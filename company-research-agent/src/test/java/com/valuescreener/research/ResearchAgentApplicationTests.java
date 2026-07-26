package com.valuescreener.research;

import com.valuescreener.research.tool.CompanyResearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ResearchAgentApplicationTests {

    @Autowired
    private CompanyResearchTool companyResearchTool;

    @Test
    void contextLoadsAndRegistersTheResearchTool() {
        assertThat(companyResearchTool).isNotNull();
    }
}
