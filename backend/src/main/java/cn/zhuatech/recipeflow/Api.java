// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 固定资源接口，状态、范围与数值均交由事务服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class Api {
  final RecipeService service;
  final AdminService admin;

  /** 连接业务及安全目录服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Api(RecipeService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 授权列表和指标，禁止动态实体名查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind}")
  public Object read(@PathVariable String kind) {
    return switch (kind) {
      case "ingredients" -> service.ingredients();
      case "recipes" -> service.recipes();
      case "batches" -> service.batches();
      case "options" -> service.options();
      case "reports" -> service.report();
      case "audit" -> service.audit();
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 新增食材报价、配方或备料需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{kind}")
  public Object create(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return switch (kind) {
      case "ingredients" -> service.saveIngredient(null, b);
      case "recipes" -> service.saveRecipe(null, b);
      case "batches" -> service.saveBatch(null, b);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 更新使用明确资源和乐观版本，禁止覆盖已发布需求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{kind}/{id}")
  public Object save(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return switch (kind) {
      case "ingredients" -> service.saveIngredient(id, b);
      case "recipes" -> service.saveRecipe(id, b);
      case "batches" -> service.saveBatch(id, b);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 读取指定业务详情和冻结快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{kind}/{id}")
  public Object detail(@PathVariable String kind, @PathVariable Long id) {
    return switch (kind) {
      case "recipes" -> service.recipeDetail(id);
      case "batches" -> service.batchDetail(id);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 未引用食材可删除，历史引用保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/ingredients/{id}")
  public Object delete(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return service.deleteIngredient(id, b);
  }

  /** 配方状态流转，当前报价试算通过独立只读入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/recipes/{id}/{action}")
  public Object recipeAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return service.recipeAction(id, action, b);
  }

  /** 当前报价试算不会生成审批或覆盖旧版成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/recipes/{id}/preview")
  public Object preview(@PathVariable Long id) {
    return service.preview(id);
  }

  /** 读取历史版本，必须属于指定配方。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/recipes/{id}/revisions/{rev}")
  public Object revision(@PathVariable Long id, @PathVariable Long rev) {
    return service.history(id, rev);
  }

  /** 备料状态与完整实际耗用由服务端原子更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/batches/{id}/{action}")
  public Object batchAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return action.equals("actual") ? service.actual(id, b) : service.batchAction(id, action, b);
  }

  /** CSV导出沿用读取和导出权限，文件名只含服务端标识。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/recipes/{id}/revisions/{rev}/export.csv")
  public ResponseEntity<String> recipeCsv(@PathVariable Long id, @PathVariable Long rev) {
    return file("recipeflow-recipe-" + id + "-r" + rev + ".csv", service.recipeCsv(id, rev));
  }

  /** 计划与完工实际导出，不接受浏览器提供的文件系统路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/batches/{id}/export.csv")
  public ResponseEntity<String> batchCsv(@PathVariable Long id) {
    return file("recipeflow-batch-" + id + ".csv", service.batchCsv(id));
  }

  ResponseEntity<String> file(String name, String s) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "attachment; filename=" + name)
        .body(s);
  }

  /** 目录不允许普通业务角色枚举全部账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object directory(@PathVariable String kind) {
    return kind.equals("options") ? admin.options() : admin.read(kind);
  }

  /** 新建安全目录，保留最后完整管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object add(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 修改安全目录，稳定身份和引用保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object update(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }
}
