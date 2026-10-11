// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 用独立手算预期验证净料率、量纲、份数与冻结报价组合。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class CostEngineTest {
  BigDecimal n(String s) {
    return new BigDecimal(s);
  }

  CostEngine.Quote quote(String price, String yield) {
    return new CostEngine.Quote(1L, "CARROT", "TEST carrot", "g", 1, n("1000"), n(price), n(yield));
  }

  void equal(String expected, BigDecimal actual) {
    assertEquals(0, n(expected).compareTo(actual), actual.toPlainString());
  }

  @Test
  void usableYieldRequiresMoreRaw() {
    var r = CostEngine.cost(quote("12", "80"), n("500"));
    equal("625", r.raw());
    equal("7.5", r.cost());
    equal("0.012", r.unitCost());
  }

  @Test
  void fullYieldHasNoLoss() {
    var r = CostEngine.cost(quote("12", "100"), n("500"));
    equal("500", r.raw());
    equal("6", r.cost());
  }

  @Test
  void kilogramIsOneThousandGrams() {
    equal("2500", CostEngine.toBase(n("2.5"), "kg", "g"));
  }

  @Test
  void litreIsOneThousandMillilitres() {
    equal("250", CostEngine.toBase(n("0.25"), "l", "ml"));
  }

  @Test
  void massCannotBeConvertedToVolume() {
    assertEquals(
        "UNIT_MISMATCH",
        assertThrows(Problem.class, () -> CostEngine.toBase(n("1"), "kg", "ml")).getMessage());
  }

  @Test
  void pieceCannotBeConvertedToMass() {
    assertThrows(Problem.class, () -> CostEngine.toBase(n("5"), "pc", "g"));
  }

  @Test
  void decimalInputDoesNotSilentlyRound() {
    assertThrows(Problem.class, () -> CostEngine.decimal("1.0001", 3, BigDecimal.ZERO, n("10")));
  }

  @Test
  void exponentInputIsRejected() {
    assertThrows(Problem.class, () -> CostEngine.decimal("1e3", 3, BigDecimal.ZERO, n("1000")));
  }

  @Test
  void negativeAndNonFiniteAreRejected() {
    for (String x : List.of("-1", "NaN", "Infinity", ""))
      assertThrows(Problem.class, () -> CostEngine.decimal(x, 3, BigDecimal.ZERO, n("10")));
  }

  @Test
  void grossQuantityRoundsOutward() {
    var r = CostEngine.cost(quote("10", "33"), n("1"));
    equal("3.030304", r.raw());
  }

  @Test
  void zeroPriceIsExplicitZero() {
    equal("0", CostEngine.cost(quote("0", "100"), n("100")).cost());
  }

  @Test
  void zeroUsableYieldIsInvalid() {
    assertThrows(Problem.class, () -> CostEngine.cost(quote("12", "0"), n("100")));
  }

  @Test
  void portionsScaleRawAndCost() {
    var r = CostEngine.cost(quote("12", "80"), n("500"));
    var x = CostEngine.prepare(List.of(new CostEngine.Demand(List.of(r), 5, 10))).getFirst();
    equal("1000", x.net());
    equal("1250", x.raw());
    equal("15", x.cost());
  }

  @Test
  void sameIngredientDifferentFrozenPricesUsesWeightedCost() {
    var old = CostEngine.cost(quote("12", "80"), n("500"));
    var newer = CostEngine.cost(quote("20", "100"), n("500"));
    var x =
        CostEngine.prepare(
                List.of(
                    new CostEngine.Demand(List.of(old), 5, 10),
                    new CostEngine.Demand(List.of(newer), 5, 5)))
            .getFirst();
    equal("1750", x.raw());
    equal("25", x.cost());
    equal("0.014285714286", x.weightedUnitCost());
  }

  @Test
  void monetaryRoundingIsHalfUp() {
    equal("1.01", CostEngine.money(n("1.005")));
    equal("1.00", CostEngine.money(n("1.004999")));
  }

  @Test
  void minimalExpandedQuantityCannotDisappear() {
    assertThrows(Problem.class, () -> CostEngine.cost(quote("12", "100"), n("0.0000001")));
  }

  @Test
  void csvFormulaPrefixIsNeutralized() {
    assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", Rules.csv("=HYPERLINK(\"x\")"));
    assertEquals("\"'  @sum\"", Rules.csv("  @sum"));
  }

  @Test
  void numericLargeQuantityKeepsItsDeclaredDecimalValue() {
    equal(
        "10000000.001",
        CostEngine.decimal(new BigDecimal("10000000.001"), 3, BigDecimal.ZERO, n("1000000000")));
  }

  @Test
  void numericExcessPrecisionMustNotDisappear() {
    assertThrows(
        Problem.class,
        () ->
            CostEngine.decimal(
                new BigDecimal("1.00000000000000001"), 4, BigDecimal.ZERO, n("1000000")));
  }

  @Test
  void multilineInstructionsNormalizeWindowsLineEndings() {
    assertEquals("first\nsecond\tstep", Rules.paragraph("first\r\nsecond\tstep", 2000, true));
  }

  @Test
  void hiddenControlCharactersAreNotAcceptedInInstructions() {
    assertThrows(Problem.class, () -> Rules.paragraph("first\u0000second", 2000, true));
  }
}
