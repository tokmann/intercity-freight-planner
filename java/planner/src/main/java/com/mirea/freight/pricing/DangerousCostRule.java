package com.mirea.freight.pricing;

import com.mirea.freight.model.Tariff;
public final class DangerousCostRule implements CostRule {
    @Override
    public double apply(double baseCost, Tariff tariff) {
        return baseCost * (1 + tariff.dangerSurchargePct() / 100);
    }
    @Override
    public boolean needsApproval() { return true; }
}
