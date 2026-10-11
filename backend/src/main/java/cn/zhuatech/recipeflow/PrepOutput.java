// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.time.*;

/** 按批准配方版本登记实际产出份数。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "prep_output")
public class PrepOutput {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "batch_id", nullable = false)
  public Long batchId;

  @Column(name = "revision_id", nullable = false)
  public Long revisionId;

  @Column(name = "portions", nullable = false)
  public int portions;

  @Column(name = "note", nullable = false, length = 400)
  public String note;
}
