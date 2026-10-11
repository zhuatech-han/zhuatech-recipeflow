// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 厨房实际毛料耗用，损耗为耗用中的子集。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "consumption")
public class Consumption {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "batch_id", nullable = false)
  public Long batchId;

  @Column(name = "ingredient_id", nullable = false)
  public Long ingredientId;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
  public BigDecimal quantity;

  @Column(name = "waste", nullable = false, precision = 18, scale = 3)
  public BigDecimal waste;

  @Column(name = "note", nullable = false, length = 400)
  public String note;
}
