package com.valuescreener.research;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Embeddable
public class FinancialStats {

    @Column(name = "pe_ratio")
    private BigDecimal peRatio;

    @Column(name = "pb_ratio")
    private BigDecimal pbRatio;

    @Column(name = "roe_percent")
    private BigDecimal roePercent;

    @Column(name = "debt_to_equity")
    private BigDecimal debtToEquity;

    @Column(name = "current_year_net_margin_percent")
    private BigDecimal currentYearNetMarginPercent;

    @Column(name = "current_year_fcf_positive")
    private Boolean currentYearFcfPositive;

    @Column(name = "current_year_net_income_increased_yoy")
    private Boolean currentYearNetIncomeIncreasedYoy;

    @Column(name = "insider_ownership_percent")
    private BigDecimal insiderOwnershipPercent;

    protected FinancialStats() {
        // JPA
    }

    public FinancialStats(BigDecimal peRatio, BigDecimal pbRatio, BigDecimal roePercent, BigDecimal debtToEquity,
                           BigDecimal currentYearNetMarginPercent, Boolean currentYearFcfPositive,
                           Boolean currentYearNetIncomeIncreasedYoy, BigDecimal insiderOwnershipPercent) {
        this.peRatio = peRatio;
        this.pbRatio = pbRatio;
        this.roePercent = roePercent;
        this.debtToEquity = debtToEquity;
        this.currentYearNetMarginPercent = currentYearNetMarginPercent;
        this.currentYearFcfPositive = currentYearFcfPositive;
        this.currentYearNetIncomeIncreasedYoy = currentYearNetIncomeIncreasedYoy;
        this.insiderOwnershipPercent = insiderOwnershipPercent;
    }

    public static FinancialStats empty() {
        return new FinancialStats(null, null, null, null, null, null, null, null);
    }

    public FinancialStats mergedWith(FinancialStats update) {
        return new FinancialStats(
                update.peRatio != null ? update.peRatio : this.peRatio,
                update.pbRatio != null ? update.pbRatio : this.pbRatio,
                update.roePercent != null ? update.roePercent : this.roePercent,
                update.debtToEquity != null ? update.debtToEquity : this.debtToEquity,
                update.currentYearNetMarginPercent != null ? update.currentYearNetMarginPercent : this.currentYearNetMarginPercent,
                update.currentYearFcfPositive != null ? update.currentYearFcfPositive : this.currentYearFcfPositive,
                update.currentYearNetIncomeIncreasedYoy != null ? update.currentYearNetIncomeIncreasedYoy : this.currentYearNetIncomeIncreasedYoy,
                update.insiderOwnershipPercent != null ? update.insiderOwnershipPercent : this.insiderOwnershipPercent);
    }

    public Set<String> presentFieldNames() {
        Set<String> fields = new LinkedHashSet<>();
        if (peRatio != null) fields.add("peRatio");
        if (pbRatio != null) fields.add("pbRatio");
        if (roePercent != null) fields.add("roePercent");
        if (debtToEquity != null) fields.add("debtToEquity");
        if (currentYearNetMarginPercent != null) fields.add("currentYearNetMarginPercent");
        if (currentYearFcfPositive != null) fields.add("currentYearFcfPositive");
        if (currentYearNetIncomeIncreasedYoy != null) fields.add("currentYearNetIncomeIncreasedYoy");
        if (insiderOwnershipPercent != null) fields.add("insiderOwnershipPercent");
        return fields;
    }

    public BigDecimal getPeRatio() { return peRatio; }
    public BigDecimal getPbRatio() { return pbRatio; }
    public BigDecimal getRoePercent() { return roePercent; }
    public BigDecimal getDebtToEquity() { return debtToEquity; }
    public BigDecimal getCurrentYearNetMarginPercent() { return currentYearNetMarginPercent; }
    public Boolean getCurrentYearFcfPositive() { return currentYearFcfPositive; }
    public Boolean getCurrentYearNetIncomeIncreasedYoy() { return currentYearNetIncomeIncreasedYoy; }
    public BigDecimal getInsiderOwnershipPercent() { return insiderOwnershipPercent; }
}
