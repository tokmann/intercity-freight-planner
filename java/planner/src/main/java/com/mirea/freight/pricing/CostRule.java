package com.mirea.freight.pricing;

import com.mirea.freight.model.Tariff;
public interface CostRule {
    double apply(double baseCost, Tariff tariff);
    boolean needsApproval();
}
