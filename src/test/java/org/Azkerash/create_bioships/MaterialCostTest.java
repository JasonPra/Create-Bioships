package org.Azkerash.create_bioships;

import org.Azkerash.create_bioships.creature.ResourceAccount;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class MaterialCostTest {
  @Test
  void insufficientMultiMaterialPaymentDoesNotPartiallySpend() {
    var stock = new MaterialLedger();
    stock.credit(MaterialCost.FLESH, 20);
    stock.credit(MaterialCost.FIBER, 10);
    var before = stock.snapshot();
    assertFalse(new MaterialCost(8, 4, 2).pay(stock));
    assertEquals(before, stock.snapshot());
  }

  @Test
  void transferAndRefundPreserveTheExactCombinedStock() {
    var stomach = new MaterialLedger();
    var core = new MaterialLedger();
    stomach.credit(MaterialCost.FLESH, 50);
    assertTrue(MaterialCost.transfer(stomach, core, MaterialCost.FLESH, 32));
    assertEquals(18, stomach.material(MaterialCost.FLESH));
    assertEquals(32, core.material(MaterialCost.FLESH));
    var price = new MaterialCost(8, 0, 0);
    assertTrue(price.pay(core));
    assertEquals(24, core.material(MaterialCost.FLESH));
    price.refund(core);
    assertEquals(50, core.material(MaterialCost.FLESH) + stomach.material(MaterialCost.FLESH));
    assertFalse(MaterialCost.transfer(stomach, core, MaterialCost.FLESH, 19));
    assertEquals(50, core.material(MaterialCost.FLESH) + stomach.material(MaterialCost.FLESH));
  }

  @Test
  void failedDebitRollsBackEarlierResources() {
    var ledger = new MaterialLedger();
    new MaterialCost(8, 4, 2).refund(ledger);
    var before = ledger.snapshot();
    var calls = new int[] {0};
    var uncertain =
        new ResourceAccount() {
          public int material(String id) {
            return ledger.material(id);
          }

          public boolean spend(String id, int count) {
            return ++calls[0] != 2 && ledger.spend(id, count);
          }

          public void credit(String id, int count) {
            ledger.credit(id, count);
          }
        };
    assertFalse(new MaterialCost(8, 4, 2).pay(uncertain));
    assertEquals(before, ledger.snapshot());
  }

  @Test
  void snapshotsCannotMutateSavedStockAndNegativeCostsAreRejected() {
    var ledger = new MaterialLedger();
    ledger.credit(MaterialCost.FLESH, 5);
    assertThrows(
        UnsupportedOperationException.class, () -> ledger.snapshot().put(MaterialCost.FLESH, 20));
    assertThrows(IllegalArgumentException.class, () -> new MaterialCost(-1, 0, 0));
  }
}
