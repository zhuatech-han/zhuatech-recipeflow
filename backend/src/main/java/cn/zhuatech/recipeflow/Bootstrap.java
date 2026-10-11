// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次初始化安全目录；配方和备料事实保持为空。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  static final List<String> CODES =
      List.of(
          "ingredients",
          "quote",
          "recipes",
          "design",
          "review",
          "batches",
          "plan",
          "execute",
          "export",
          "reports",
          "users",
          "roles",
          "settings",
          "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  /** 配置凭据仅用于空数据库，绝不打印密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${recipeflow.admin-username}") String username,
      @Value("${recipeflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 创建角色、分类、菜单及独立管理员；重启不覆盖任何业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-zA-Z0-9_.-]{3,60}"), "INVALID_INPUT");
    for (String code : CODES) {
      var p = new Permission();
      p.code = code;
      p.name = code;
      db.save(p);
    }
    var d = new Department();
    d.name = "主厨房 / Main kitchen";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var admin = role("管理员 / Administrator", "ALL", CODES);
    role(
        "配方研发 / Recipe author",
        "DEPARTMENT",
        List.of("ingredients", "quote", "recipes", "design", "export", "reports"));
    role("独立审核 / Reviewer", "ASSIGNED", List.of("recipes", "review", "export"));
    role(
        "备料计划 / Preparation planner",
        "DEPARTMENT",
        List.of("recipes", "batches", "plan", "export", "reports"));
    role("厨房执行 / Kitchen operator", "ASSIGNED", List.of("batches", "execute", "export"));
    role(
        "部门查阅 / Reader",
        "DEPARTMENT",
        List.of("ingredients", "recipes", "batches", "export", "reports", "audit"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    db.save(a);
    String[][] menus = {
      {"recipes", "标准配方", "Recipes", "recipes"},
      {"batches", "备料计划", "Preparation", "batches"},
      {"ingredients", "食材与报价", "Ingredients", "ingredients"},
      {"reports", "成本与执行统计", "Reports", "reports"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles", "roles"},
      {"settings", "厨房与设置", "Settings", "settings"},
      {"audit", "审计记录", "Audit", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var v :
        new String[][] {
          {"PRODUCE", "蔬果", "Produce"},
          {"PROTEIN", "肉蛋豆制品", "Protein"},
          {"DRY", "干货与调味", "Dry & seasoning"},
          {"OTHER", "其他", "Other"}
        }) {
      var x = new DictionaryEntry();
      x.type = "INGREDIENT";
      x.code = v[0];
      x.name = v[1];
      x.nameEn = v[2];
      db.save(x);
    }
    setting("max_recipes", "500");
    setting("max_batches", "500");
    setting("max_revisions", "30");
  }

  private AccessRole role(String name, String scope, List<String> codes) {
    var x = new AccessRole();
    x.name = name;
    x.scope = scope;
    x.permissions.addAll(codes);
    return db.save(x);
  }

  private void setting(String code, String value) {
    var x = new SystemSetting();
    x.code = code;
    x.value = value;
    db.save(x);
  }
}
