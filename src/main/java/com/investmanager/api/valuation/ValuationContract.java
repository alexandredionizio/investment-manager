package com.investmanager.api.valuation;

import jakarta.persistence.*;

@Entity
@Table(name="valuation_contracts", uniqueConstraints=@UniqueConstraint(columnNames={"portfolio_id","asset_id"}))
public class ValuationContract {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="portfolio_id", nullable=false) private Long portfolioId;
    @Column(name="asset_id", nullable=false) private Long assetId;
    @Column(name="terms_json", nullable=false, columnDefinition="text") private String termsJson;
    protected ValuationContract() {}
    public ValuationContract(Long portfolioId, Long assetId, String termsJson) {
        this.portfolioId=portfolioId; this.assetId=assetId; this.termsJson=termsJson;
    }
    public String getTermsJson() { return termsJson; }
    public void setTermsJson(String value) { termsJson=value; }
}
