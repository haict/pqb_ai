package com.company.erp.sales.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Dòng báo giá (BR-SAL-01). */
@Entity
@Table(name = "sales_quote_lines")
public class SalesQuoteLine extends SalesPricedLine {

    protected SalesQuoteLine() {
    }

    public SalesQuoteLine(int lineNo, PricedValues values) {
        super(lineNo);
        applyPrice(values);
    }

    void copyFrom(SalesQuoteLine other) {
        applyPrice(other.priceValues());
    }

    /** Giá trị để sao sang phiên bản mới / đơn bán. */
    public PricedValues values() {
        return priceValues();
    }
}
