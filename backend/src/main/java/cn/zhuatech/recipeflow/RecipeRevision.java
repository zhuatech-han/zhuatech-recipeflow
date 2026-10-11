// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.time.*;

/** 送审成本快照、校验摘要与不可变批准事实。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "recipe_revision")
public class RecipeRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "recipe_id", nullable = false)
  public Long recipeId;

  @Column(name = "number", nullable = false)
  public int number;

  @Column(name = "snapshot_json", nullable = false, columnDefinition = "longtext")
  public String snapshotJson;

  @Column(name = "digest", nullable = false, length = 64)
  public String digest;

  @Column(name = "decision", nullable = false, length = 20)
  public String decision;

  @Column(name = "submitted_by", nullable = false)
  public Long submittedBy;

  @Column(name = "reviewed_by", nullable = true)
  public Long reviewedBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
