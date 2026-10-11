// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 食材报价、计量维度与净料率，不包含库存余额。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ingredient")
public class Ingredient {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 30)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "unit", nullable = false, length = 10)
  public String unit;

  @Column(name = "purchase_quantity", nullable = false, precision = 18, scale = 3)
  public BigDecimal purchaseQuantity;

  @Column(name = "purchase_price", nullable = false, precision = 18, scale = 4)
  public BigDecimal purchasePrice;

  @Column(name = "yield_percent", nullable = false, precision = 8, scale = 2)
  public BigDecimal yieldPercent;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
