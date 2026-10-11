// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.time.*;

/** 保留所有历史编辑人以防自我审核。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "recipe_editor")
public class RecipeEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "recipe_id", nullable = false)
  public Long recipeId;

  @Column(name = "account_id", nullable = false)
  public Long accountId;
}
