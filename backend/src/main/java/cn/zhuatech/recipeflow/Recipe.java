// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.time.*;

/** 稳定配方身份、草稿和指定独立审核人。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "recipe")
public class Recipe {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 30)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "portions", nullable = false)
  public int portions;

  @Column(name = "instructions", nullable = false, length = 2000)
  public String instructions;

  @Column(name = "state", nullable = false, length = 20)
  public String state;

  @Column(name = "version", nullable = false)
  public long version = 1;

  @Column(name = "active_revision_id", nullable = true)
  public Long activeRevisionId;
}
