package com.valuescreener.research.group;

import java.util.List;

/** The initial stock group catalog (spec section 6.5); single source in code, seeded once by {@link StockGroupSeeder}. */
final class StockGroupSeed {

    static final String MARKER_SK = "SEED#stock-groups-v1";

    static final List<StockGroup> GROUPS = List.of(
            new StockGroup("BANKS", "Banks", 10),
            new StockGroup("INSURANCE", "Insurance", 20),
            new StockGroup("ASSET_MANAGEMENT", "Asset Management", 30),
            new StockGroup("CAPITAL_MARKETS", "Exchanges & Capital Markets", 40),
            new StockGroup("PAYMENTS_FINTECH", "Payments & Fintech", 50),
            new StockGroup("HOLDING_COMPANIES", "Holding Companies", 60),
            new StockGroup("OIL_GAS", "Oil & Gas", 70),
            new StockGroup("RENEWABLE_ENERGY", "Renewable Energy", 80),
            new StockGroup("UTILITIES", "Utilities", 90),
            new StockGroup("CHEMICALS", "Chemicals", 100),
            new StockGroup("MINING_METALS", "Mining & Metals", 110),
            new StockGroup("CONSTRUCTION_MATERIALS", "Construction & Building Materials", 120),
            new StockGroup("INDUSTRIAL_MACHINERY", "Industrial Machinery", 130),
            new StockGroup("ELECTRICAL_EQUIPMENT", "Electrical Equipment & Automation", 140),
            new StockGroup("AEROSPACE_DEFENSE", "Aerospace & Defense", 150),
            new StockGroup("TRANSPORTATION_LOGISTICS", "Transportation & Logistics", 160),
            new StockGroup("AUTOMOTIVE", "Automotive & Suppliers", 170),
            new StockGroup("BUSINESS_SERVICES", "Business Services", 180),
            new StockGroup("CONSUMER_SERVICES", "Consumer Services", 190),
            new StockGroup("CONSUMER_GOODS", "Consumer Goods", 200),
            new StockGroup("RETAIL_ECOMMERCE", "Retail & E-Commerce", 210),
            new StockGroup("PHARMA", "Pharma", 220),
            new StockGroup("BIOTECH", "Biotech", 230),
            new StockGroup("MEDICAL_TECHNOLOGY", "Medical Technology", 240),
            new StockGroup("HEALTHCARE_SERVICES", "Healthcare Services", 250),
            new StockGroup("SEMICONDUCTORS", "Semiconductors", 260),
            new StockGroup("SOFTWARE", "Software", 270),
            new StockGroup("CYBERSECURITY", "Cybersecurity", 280),
            new StockGroup("IT_SERVICES", "IT Services", 290),
            new StockGroup("HARDWARE_ELECTRONICS", "Hardware & Electronics", 300),
            new StockGroup("INTERNET_PLATFORMS", "Internet Platforms", 310),
            new StockGroup("MEDIA_ENTERTAINMENT", "Media & Entertainment", 320),
            new StockGroup("TELECOMMUNICATIONS", "Telecommunications", 330),
            new StockGroup("REAL_ESTATE", "Real Estate", 340),
            new StockGroup("OTHER", "Other", 350));

    private StockGroupSeed() {
    }
}
