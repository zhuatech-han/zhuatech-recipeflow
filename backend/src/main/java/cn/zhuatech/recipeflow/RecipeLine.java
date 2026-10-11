// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 配方明细只引用食材或已批准子配方版本。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "recipe_line")
public class RecipeLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "recipe_id", nullable = false)
  public Long recipeId;

  @Column(name = "ingredient_id", nullable = true)
  public Long ingredientId;

  @Column(name = "component_revision_id", nullable = true)
  public Long componentRevisionId;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
  public BigDecimal quantity;

  @Column(name = "unit", nullable = false, length = 10)
  public String unit;
}
