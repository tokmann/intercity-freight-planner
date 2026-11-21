package com.mirea.freight.pricing;

import com.mirea.freight.model.Tariff;
public final class StandardCostRule implements CostRule {
    @Override
    public double apply(double baseCost, Tariff tariff) { return baseCost; }
    @Override
    public boolean needsApproval() { return false; }
}
