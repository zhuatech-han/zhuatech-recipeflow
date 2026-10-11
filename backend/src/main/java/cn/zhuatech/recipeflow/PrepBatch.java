// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import jakarta.persistence.*;
import java.time.*;

/** 备料计划冻结需求和厨房执行结果。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "prep_batch")
public class PrepBatch {
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

  @Column(name = "operator_id", nullable = false)
  public Long operatorId;

  @Column(name = "service_date", nullable = false)
  public LocalDate serviceDate;

  @Column(name = "state", nullable = false, length = 20)
  public String state;

  @Column(name = "version", nullable = false)
  public long version = 1;

  @Column(name = "snapshot_json", nullable = false, columnDefinition = "longtext")
  public String snapshotJson;

  @Column(name = "digest", nullable = false, length = 64)
  public String digest;

  @Column(name = "result_json", nullable = true, columnDefinition = "longtext")
  public String resultJson;
}
