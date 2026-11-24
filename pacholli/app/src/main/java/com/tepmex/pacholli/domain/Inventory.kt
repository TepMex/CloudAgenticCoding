package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Average-cost lot. Quantity is debit-positive; cost is the base still tied up in that quantity. */
data class Lot(val qty: BigDecimal, val cost: BigDecimal) {
    companion object {
        val EMPTY = Lot(Money.ZERO, Money.ZERO)
    }
}

object Inventory {
    /**
     * Apply one posting. The returned amount is realized gain in the operating currency
     * (positive means a gain). A flat position keeps no residual cost.
     */
    fun apply(lot: Lot, side: Side, quantity: BigDecimal, base: BigDecimal): Pair<BigDecimal, Lot> {
        val qtyIn = Money.s(quantity)
        val baseIn = Money.s(base)
        val deltaQty = if (side == Side.DEBIT) qtyIn else qtyIn.negate()
        val deltaBase = if (side == Side.DEBIT) baseIn else baseIn.negate()
        if (lot.qty.signum() == 0 || deltaQty.signum() == lot.qty.signum()) {
            return dust(
                BigDecimal.ZERO,
                Lot(lot.qty.add(deltaQty), lot.cost.add(deltaBase)),
            )
        }
        val closing = deltaQty.abs().min(lot.qty.abs())
        val avg = lot.cost.divide(lot.qty, Money.SCALE, RoundingMode.HALF_UP)
        val postedPer = deltaBase.divide(deltaQty, Money.SCALE, RoundingMode.HALF_UP)
        val proceeds = postedPer.multiply(closing)
        val closedCost = avg.multiply(closing)
        val realized = if (lot.qty.signum() > 0) {
            proceeds.subtract(closedCost)
        } else {
            closedCost.subtract(proceeds)
        }
        val sign = BigDecimal.valueOf(lot.qty.signum().toLong())
        var qty = lot.qty.subtract(sign.multiply(closing))
        var cost = lot.cost.subtract(avg.multiply(sign).multiply(closing))
        val leftover = deltaQty.abs().subtract(closing)
        if (leftover.signum() > 0) {
            val leftBase = postedPer.multiply(leftover)
            if (deltaQty.signum() > 0) {
                qty = qty.add(leftover)
                cost = cost.add(leftBase)
            } else {
                qty = qty.subtract(leftover)
                cost = cost.subtract(leftBase)
            }
        }
        return dust(realized, Lot(qty, cost))
    }

    private fun dust(realized: BigDecimal, lot: Lot): Pair<BigDecimal, Lot> {
        val qty = Money.s(lot.qty)
        val cost = Money.s(lot.cost)
        if (qty.signum() == 0 && cost.signum() != 0) {
            return Money.s(realized.subtract(cost)) to Lot.EMPTY
        }
        return Money.s(realized) to Lot(qty, cost)
    }
}
