// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import java.math.*;
import java.util.*;

/** 确定性十进制食材成本及备料换算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class CostEngine {
  public static final BigDecimal HUNDRED = new BigDecimal("100");

  /** 冻结食材基准报价；unit 为 g/ml/pc。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Quote(
      Long ingredientId,
      String code,
      String name,
      String unit,
      long priceVersion,
      BigDecimal purchaseBase,
      BigDecimal purchasePrice,
      BigDecimal yieldPercent) {}

  /** 快照行存净料、毛料、报价和未舍入为分的成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CostRow(
      Long ingredientId,
      String code,
      String name,
      String unit,
      long priceVersion,
      BigDecimal net,
      BigDecimal raw,
      BigDecimal unitCost,
      BigDecimal cost,
      BigDecimal yieldPercent) {}

  /** 批次汇总保留各配方冻结价格和净料率的加权贡献。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Requirement(
      Long ingredientId,
      String code,
      String name,
      String unit,
      BigDecimal net,
      BigDecimal raw,
      BigDecimal cost,
      BigDecimal weightedUnitCost) {}

  /** 一组已批准配方按供餐份数放大。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Demand(List<CostRow> rows, int recipePortions, int requestedPortions) {}

  /** 严格十进制输入，超精度、指数文本和超限一律拒绝；不隐式四舍五入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal decimal(Object value, int scale, BigDecimal min, BigDecimal max) {
    try {
      String s =
          value instanceof Number
              ? new BigDecimal(value.toString()).toPlainString()
              : Objects.toString(value, "");
      if (!s.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException();
      var x = new BigDecimal(s).setScale(scale, RoundingMode.UNNECESSARY);
      if (x.compareTo(min) < 0 || x.compareTo(max) > 0) throw new IllegalArgumentException();
      return x;
    } catch (Exception e) {
      throw new Problem(400, "INVALID_DECIMAL");
    }
  }

  /** 单位仅在同一维度内转换，严禁默认密度推算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String baseUnit(String unit) {
    return switch (unit) {
      case "g", "kg" -> "g";
      case "ml", "l" -> "ml";
      case "pc" -> "pc";
      default -> throw new Problem(400, "UNIT_INVALID");
    };
  }

  /** 转换 kg→g、l→ml；计数和维度必须显式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal toBase(BigDecimal quantity, String unit, String expected) {
    Rules.check(baseUnit(unit).equals(expected), "UNIT_MISMATCH");
    return quantity.multiply(
        Set.of("kg", "l").contains(unit) ? new BigDecimal("1000") : BigDecimal.ONE);
  }

  /** 净料向上换算毛料至六位小数，单价12位、行成本6位，最终金额另舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static CostRow cost(Quote q, BigDecimal net) {
    Rules.check(
        net.signum() > 0 && net.compareTo(new BigDecimal("1000000000")) <= 0, "QUANTITY_LIMIT");
    Rules.check(
        q.purchaseBase.signum() > 0
            && q.purchasePrice.signum() >= 0
            && q.yieldPercent.signum() > 0
            && q.yieldPercent.compareTo(HUNDRED) <= 0,
        "INVALID_DECIMAL");
    var n = net.setScale(6, RoundingMode.HALF_UP);
    Rules.check(n.signum() > 0, "QUANTITY_TOO_SMALL");
    var raw = n.multiply(HUNDRED).divide(q.yieldPercent, 6, RoundingMode.CEILING);
    var unit = q.purchasePrice.divide(q.purchaseBase, 12, RoundingMode.HALF_UP);
    return new CostRow(
        q.ingredientId,
        q.code,
        q.name,
        q.unit,
        q.priceVersion,
        n,
        raw,
        unit,
        raw.multiply(unit).setScale(6, RoundingMode.HALF_UP),
        q.yieldPercent);
  }

  /** 总金额最后舍入为分；不将食材成本标为完整利润。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal total(List<CostRow> rows) {
    return money(rows.stream().map(CostRow::cost).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  /** 供餐需求分别使用各配方冻结净料率再按食材归并。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Requirement> prepare(List<Demand> demands) {
    Map<Long, List<CostRow>> grouped = new TreeMap<>();
    for (var d : demands) {
      Rules.check(d.recipePortions > 0 && d.requestedPortions > 0, "INVALID_INPUT");
      for (var row : d.rows) {
        var net =
            row.net
                .multiply(new BigDecimal(d.requestedPortions))
                .divide(new BigDecimal(d.recipePortions), 12, RoundingMode.HALF_UP);
        var raw = net.multiply(HUNDRED).divide(row.yieldPercent, 6, RoundingMode.CEILING);
        var r =
            new CostRow(
                row.ingredientId,
                row.code,
                row.name,
                row.unit,
                row.priceVersion,
                net,
                raw,
                row.unitCost,
                raw.multiply(row.unitCost).setScale(6, RoundingMode.HALF_UP),
                row.yieldPercent);
        grouped.computeIfAbsent(r.ingredientId, k -> new ArrayList<>()).add(r);
      }
    }
    List<Requirement> out = new ArrayList<>();
    for (var list : grouped.values()) {
      var first = list.getFirst();
      Rules.check(list.stream().allMatch(x -> x.unit.equals(first.unit)), "UNIT_MISMATCH");
      var net = BigDecimal.ZERO;
      var raw = BigDecimal.ZERO;
      var cost = BigDecimal.ZERO;
      for (var x : list) {
        net = net.add(x.net);
        raw = raw.add(x.raw);
        cost = cost.add(x.cost);
      }
      Rules.check(raw.compareTo(new BigDecimal("1000000000")) <= 0, "QUANTITY_LIMIT");
      out.add(
          new Requirement(
              first.ingredientId,
              first.code,
              first.name,
              first.unit,
              net.setScale(6, RoundingMode.HALF_UP),
              raw,
              cost,
              cost.divide(raw, 12, RoundingMode.HALF_UP)));
    }
    Rules.check(out.size() <= 100, "RESOURCE_LIMIT");
    return List.copyOf(out);
  }

  /** 币种为单一人民币，HALF_UP 到分。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  private CostEngine() {}
}
