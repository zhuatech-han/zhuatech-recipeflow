// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.time.*;

/** 应用追加的配方和备料状态事件。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "business_event")
public class BusinessEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "object_id", nullable = false)
  public Long objectId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "action", nullable = false, length = 30)
  public String action;

  @Column(name = "note", nullable = false, length = 400)
  public String note;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
