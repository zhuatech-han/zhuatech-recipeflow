// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 配方审批与厨房执行的事务边界，价格快照不随目录改价重算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class RecipeService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  /** 已批准子配方引用及用量；保存版本，不引用可变草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Component(
      Long recipeId, Long revisionId, BigDecimal portions, String name, String instructions) {}

  /** 配方成本快照包含完整展开食材和所有依赖身份，阻止跨版本循环。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record RecipeSnapshot(
      Long recipeId,
      String code,
      String name,
      Long departmentId,
      int portions,
      String instructions,
      List<Component> components,
      List<Long> dependencies,
      int depth,
      List<CostEngine.CostRow> rows,
      BigDecimal totalCost,
      BigDecimal portionCost,
      String capturedAt) {}

  /** 备料菜品保留批准版本及完整配方快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Dish(
      Long revisionId, int portions, RecipeSnapshot recipe, List<Component> componentTasks) {}

  /** 发布需求快照不依赖当前报价和配方目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Preparation(
      List<Dish> dishes, List<CostEngine.Requirement> requirements, BigDecimal totalCost) {}

  /** 实际耗用以冻结计划加权单价估值；损耗包含在耗用中。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Actual(
      Long ingredientId,
      BigDecimal quantity,
      BigDecimal waste,
      String note,
      BigDecimal estimatedCost,
      BigDecimal quantityVariance,
      BigDecimal costVariance) {}

  /** 最终执行结果只读保存，不冒充真实采购或财务结算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Result(
      List<Actual> consumption,
      List<PrepOutput> outputs,
      BigDecimal estimatedActualCost,
      BigDecimal costVariance,
      String completedAt) {}

  /** 连接安全目录、数据库及可测试时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public RecipeService(Store db, AccessService access, AdminService admin, Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
  }

  Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encode snapshot", e);
    }
  }

  <T> T decode(String s, Class<T> type) {
    try {
      return json.readValue(s, type);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored snapshot", e);
    }
  }

  String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  void scope(boolean ok) {
    if (!ok) throw new Problem(403, "OUT_OF_SCOPE");
  }

  void write(String permission) {
    admin.lock();
    access.require(permission);
  }

  void version(long v, Map<String, Object> b) {
    Rules.check(v == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  String code(Object o) {
    String c = Rules.text(o, 30, true);
    Rules.check(c.matches("[A-Z0-9_]{2,30}"), "CODE_INVALID");
    return c;
  }

  String note(Map<String, Object> b) {
    String n = Rules.paragraph(b.get("note"), 400, true);
    Rules.check(n.length() >= 4, "NOTE_REQUIRED");
    return n;
  }

  Long department(Object v) {
    Long id = Rules.id(v);
    scope(access.department(id));
    Rules.check(db.get(Department.class, id).enabled, "DEPARTMENT_DISABLED");
    return id;
  }

  void assigned(Long account, Long dept, String permission, String error) {
    var a = db.get(Account.class, account);
    var r = db.get(AccessRole.class, a.roleId);
    Rules.check(
        a.enabled
            && db.get(Department.class, a.departmentId).enabled
            && Objects.equals(a.departmentId, dept)
            && r.permissions.contains(permission),
        error);
  }

  boolean edited(Long recipe, Long account) {
    return !db.query(
            RecipeEditor.class,
            "from RecipeEditor where recipeId=?1 and accountId=?2",
            recipe,
            account)
        .isEmpty();
  }

  void editor(Recipe r) {
    if (!edited(r.id, access.current().id)) {
      var x = new RecipeEditor();
      x.recipeId = r.id;
      x.accountId = access.current().id;
      db.save(x);
    }
  }

  boolean visible(Recipe r) {
    var a = access.current();
    return access.role().scope.equals("ALL")
        || Objects.equals(r.departmentId, a.departmentId)
            && (access.role().scope.equals("DEPARTMENT")
                || Objects.equals(r.createdBy, a.id)
                || Objects.equals(r.reviewerId, a.id)
                || edited(r.id, a.id));
  }

  boolean visible(PrepBatch r) {
    var a = access.current();
    return access.role().scope.equals("ALL")
        || Objects.equals(r.departmentId, a.departmentId)
            && (access.role().scope.equals("DEPARTMENT")
                || Objects.equals(r.createdBy, a.id)
                || Objects.equals(r.operatorId, a.id));
  }

  Recipe recipe(Long id) {
    access.require("recipes");
    var r = db.get(Recipe.class, id);
    scope(visible(r));
    return r;
  }

  PrepBatch batch(Long id) {
    access.require("batches");
    var b = db.get(PrepBatch.class, id);
    scope(visible(b));
    return b;
  }

  List<RecipeLine> lines(Long id) {
    return db.query(RecipeLine.class, "from RecipeLine where recipeId=?1 order by id", id);
  }

  List<RecipeRevision> revisions(Long id) {
    return db.query(
        RecipeRevision.class, "from RecipeRevision where recipeId=?1 order by number desc", id);
  }

  /** 验证历史摘要后读取配方快照，不相信客户端提供的计算结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  RecipeSnapshot snapshot(RecipeRevision rev) {
    Rules.check(hash(rev.snapshotJson).equals(rev.digest), "SNAPSHOT_CHANGED");
    return decode(rev.snapshotJson, RecipeSnapshot.class);
  }

  /** 验证计划摘要并恢复冻结需求，不读取当前报价重新计价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  Preparation preparation(PrepBatch b) {
    Rules.check(hash(b.snapshotJson).equals(b.digest), "SNAPSHOT_CHANGED");
    return decode(b.snapshotJson, Preparation.class);
  }

  void event(String kind, Long id, Long dept, String action, String note) {
    var e = new BusinessEvent();
    e.kind = kind;
    e.objectId = id;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = now();
    db.save(e);
    access.audit(kind + "_" + action, id, dept);
  }

  Map<String, Object> revisionView(RecipeRevision r) {
    return Map.of(
        "id",
        r.id,
        "number",
        r.number,
        "decision",
        r.decision,
        "createdAt",
        r.createdAt,
        "submittedBy",
        r.submittedBy,
        "reviewedBy",
        r.reviewedBy == null ? 0 : r.reviewedBy,
        "digest",
        r.digest);
  }

  List<BusinessEvent> events(String kind, Long id) {
    return db.query(
        BusinessEvent.class,
        "from BusinessEvent where kind=?1 and objectId=?2 order by id",
        kind,
        id);
  }

  /** 查询食材报价目录；ASSIGNED不扩大厨房范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Ingredient> ingredients() {
    access.require("ingredients");
    return db.all(Ingredient.class).stream()
        .filter(i -> access.department(i.departmentId))
        .toList();
  }

  /** 新增与改价不更新已批准快照；稳定编号、厨房和维度不可更改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Ingredient saveIngredient(Long id, Map<String, Object> b) {
    write("quote");
    access.require("ingredients");
    var i = id == null ? new Ingredient() : db.get(Ingredient.class, id);
    if (id != null) {
      scope(access.department(i.departmentId));
      version(i.version, b);
    }
    var dept = department(b.get("departmentId"));
    var c = code(b.get("code"));
    String unit = Rules.text(b.get("unit"), 10, true);
    CostEngine.baseUnit(unit);
    if (id != null)
      Rules.check(
          i.code.equals(c)
              && i.departmentId.equals(dept)
              && CostEngine.baseUnit(i.unit).equals(CostEngine.baseUnit(unit)),
          "IDENTITY_LOCKED");
    String category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        db
            .query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='INGREDIENT' and code=?1",
                category)
            .stream()
            .anyMatch(x -> x.enabled),
        "CATEGORY_INVALID");
    i.code = c;
    i.departmentId = dept;
    i.unit = unit;
    i.category = category;
    i.name = Rules.text(b.get("name"), 120, true);
    i.purchaseQuantity =
        CostEngine.decimal(
            b.get("purchaseQuantity"), 3, new BigDecimal("0.001"), new BigDecimal("1000000"));
    i.purchasePrice =
        CostEngine.decimal(b.get("purchasePrice"), 4, BigDecimal.ZERO, new BigDecimal("1000000"));
    i.yieldPercent =
        CostEngine.decimal(b.get("yieldPercent"), 2, new BigDecimal("0.01"), CostEngine.HUNDRED);
    i.enabled = Rules.flag(b.get("enabled"));
    if (id == null) {
      Rules.check(db.all(Ingredient.class).size() < 1000, "RESOURCE_LIMIT");
      db.save(i);
    } else i.version++;
    event("INGREDIENT", i.id, i.departmentId, "SAVE", "");
    return i;
  }

  /** 只删除未引用食材；已用于历史配方应停用保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Boolean> deleteIngredient(Long id, Map<String, Object> b) {
    write("quote");
    access.require("ingredients");
    var i = db.get(Ingredient.class, id);
    scope(access.department(i.departmentId));
    version(i.version, b);
    Rules.check(
        db.query(RecipeLine.class, "from RecipeLine where ingredientId=?1", id).isEmpty()
            && db.all(RecipeRevision.class).stream()
                .noneMatch(
                    x -> snapshot(x).rows.stream().anyMatch(row -> row.ingredientId().equals(id))),
        "REFERENCED");
    event("INGREDIENT", id, i.departmentId, "DELETE", "");
    db.delete(i);
    return Map.of("ok", true);
  }

  /** 全部选项经过厨房过滤；仅批准配方作为共享的备料与子配方目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> options() {
    Rules.check(
        access.has("recipes") || access.has("batches") || access.has("ingredients"), "FORBIDDEN");
    var a = access.current();
    var users =
        db.all(Account.class).stream()
            .filter(
                x ->
                    x.enabled
                        && access.department(x.departmentId)
                        && db.get(Department.class, x.departmentId).enabled)
            .map(
                x ->
                    Map.of(
                        "id",
                        x.id,
                        "name",
                        x.displayName,
                        "departmentId",
                        x.departmentId,
                        "review",
                        db.get(AccessRole.class, x.roleId).permissions.contains("review"),
                        "execute",
                        db.get(AccessRole.class, x.roleId).permissions.contains("execute")))
            .toList();
    var published =
        db.all(Recipe.class).stream()
            .filter(x -> x.state.equals("APPROVED") && access.department(x.departmentId))
            .map(
                x ->
                    Map.of(
                        "id",
                        x.id,
                        "revisionId",
                        x.activeRevisionId,
                        "code",
                        x.code,
                        "name",
                        x.name,
                        "departmentId",
                        x.departmentId,
                        "portions",
                        x.portions))
            .toList();
    return Map.of(
        "departments",
        db.all(Department.class).stream()
            .filter(x -> x.enabled && access.department(x.id))
            .toList(),
        "users",
        users,
        "published",
        published,
        "ingredients",
        access.has("ingredients") || access.has("design")
            ? db.all(Ingredient.class).stream()
                .filter(x -> access.department(x.departmentId))
                .toList()
            : List.of(),
        "categories",
        db.all(DictionaryEntry.class).stream()
            .filter(x -> x.type.equals("INGREDIENT") && x.enabled)
            .toList(),
        "self",
        a.id);
  }

  /** 配方列表只呈现实时授权范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Recipe> recipes() {
    access.require("recipes");
    return db.all(Recipe.class).stream().filter(this::visible).toList();
  }

  /** 编辑完整草稿；提交版本锁保护被覆盖，编辑人永久保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveRecipe(Long id, Map<String, Object> b) {
    write("design");
    access.require("recipes");
    var r = id == null ? new Recipe() : recipe(id);
    if (id == null) {
      Rules.check(db.all(Recipe.class).size() < admin.setting("max_recipes"), "RESOURCE_LIMIT");
      r.createdBy = access.current().id;
      r.state = "DRAFT";
    } else {
      version(r.version, b);
      Rules.check(r.state.equals("DRAFT"), "STATE_INVALID");
    }
    Long dept = department(b.get("departmentId"));
    String c = code(b.get("code"));
    if (id != null) Rules.check(r.code.equals(c) && r.departmentId.equals(dept), "IDENTITY_LOCKED");
    r.departmentId = dept;
    r.code = c;
    r.name = Rules.text(b.get("name"), 120, true);
    r.portions = Rules.integer(b.get("portions"), 1, 10000);
    r.instructions = Rules.paragraph(b.get("instructions"), 2000, true);
    r.reviewerId = Rules.id(b.get("reviewerId"));
    assigned(r.reviewerId, dept, "review", "REVIEWER_UNAVAILABLE");
    if (id == null) db.save(r);
    else {
      r.version++;
      for (var x : lines(id)) db.delete(x);
    }
    editor(r);
    if (!(b.get("lines") instanceof List<?> list) || list.isEmpty() || list.size() > 40)
      throw new Problem(400, "LINE_LIMIT");
    for (var o : list) {
      if (!(o instanceof Map<?, ?> x)) throw new Problem(400, "INVALID_INPUT");
      var l = new RecipeLine();
      l.recipeId = r.id;
      l.quantity =
          CostEngine.decimal(
              x.get("quantity"), 3, new BigDecimal("0.001"), new BigDecimal("1000000"));
      l.unit = Rules.text(x.get("unit"), 10, true);
      boolean ingredient = x.get("ingredientId") != null,
          component = x.get("componentRevisionId") != null;
      Rules.check(ingredient != component, "LINE_INVALID");
      if (ingredient) {
        l.ingredientId = Rules.id(x.get("ingredientId"));
        var i = db.get(Ingredient.class, l.ingredientId);
        Rules.check(i.enabled && i.departmentId.equals(dept), "INGREDIENT_UNAVAILABLE");
        CostEngine.toBase(l.quantity, l.unit, CostEngine.baseUnit(i.unit));
      } else {
        l.componentRevisionId = Rules.id(x.get("componentRevisionId"));
        var rev = db.get(RecipeRevision.class, l.componentRevisionId);
        var snap = snapshot(rev);
        Rules.check(
            rev.decision.equals("APPROVED") && snap.departmentId.equals(dept),
            "COMPONENT_UNAVAILABLE");
        Rules.check(l.unit.equals("portion"), "UNIT_INVALID");
        Rules.check(!snap.dependencies.contains(r.id), "RECIPE_CYCLE");
      }
      db.save(l);
    }
    build(r);
    event("RECIPE", r.id, r.departmentId, "SAVE", "");
    return recipeDetail(r.id);
  }

  /** 展开批准子配方净料、保留制作步骤、捕获当前食材报价并拒绝循环与超限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  RecipeSnapshot build(Recipe r) {
    Map<Long, BigDecimal> quantities = new TreeMap<>();
    List<Component> components = new ArrayList<>();
    Set<Long> deps = new TreeSet<>();
    deps.add(r.id);
    int depth = 1;
    for (var l : lines(r.id)) {
      if (l.ingredientId != null) {
        var i = db.get(Ingredient.class, l.ingredientId);
        Rules.check(i.enabled && i.departmentId.equals(r.departmentId), "INGREDIENT_UNAVAILABLE");
        var n = CostEngine.toBase(l.quantity, l.unit, CostEngine.baseUnit(i.unit));
        quantities.merge(i.id, n, BigDecimal::add);
      } else {
        var rev = db.get(RecipeRevision.class, l.componentRevisionId);
        var s = snapshot(rev);
        Rules.check(
            rev.decision.equals("APPROVED") && s.departmentId.equals(r.departmentId),
            "COMPONENT_UNAVAILABLE");
        Rules.check(!s.dependencies.contains(r.id), "RECIPE_CYCLE");
        depth = Math.max(depth, s.depth + 1);
        deps.addAll(s.dependencies);
        components.add(new Component(s.recipeId, rev.id, l.quantity, s.name, s.instructions));
        for (var c : s.components) {
          var portions =
              c.portions
                  .multiply(l.quantity)
                  .divide(new BigDecimal(s.portions), 12, RoundingMode.HALF_UP);
          components.add(new Component(c.recipeId, c.revisionId, portions, c.name, c.instructions));
        }
        Rules.check(components.size() <= 200, "COMPONENT_LIMIT");
        for (var row : s.rows) {
          var n =
              row.net()
                  .multiply(l.quantity)
                  .divide(new BigDecimal(s.portions), 12, RoundingMode.HALF_UP);
          quantities.merge(row.ingredientId(), n, BigDecimal::add);
        }
      }
    }
    Rules.check(depth <= 5 && quantities.size() <= 100 && !quantities.isEmpty(), "RECIPE_DEPTH");
    List<CostEngine.CostRow> rows = new ArrayList<>();
    for (var e : quantities.entrySet()) {
      var i = db.get(Ingredient.class, e.getKey());
      Rules.check(i.enabled && i.departmentId.equals(r.departmentId), "INGREDIENT_UNAVAILABLE");
      rows.add(
          CostEngine.cost(
              new CostEngine.Quote(
                  i.id,
                  i.code,
                  i.name,
                  CostEngine.baseUnit(i.unit),
                  i.version,
                  CostEngine.toBase(i.purchaseQuantity, i.unit, CostEngine.baseUnit(i.unit)),
                  i.purchasePrice,
                  i.yieldPercent),
              e.getValue()));
    }
    var total = CostEngine.total(rows);
    return new RecipeSnapshot(
        r.id,
        r.code,
        r.name,
        r.departmentId,
        r.portions,
        r.instructions,
        List.copyOf(components),
        List.copyOf(deps),
        depth,
        List.copyOf(rows),
        total,
        total.divide(new BigDecimal(r.portions), 4, RoundingMode.HALF_UP),
        now().toString());
  }

  /** 详情同时呈现草稿明细与历史摘要，后台不返回无限的审计集合。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> recipeDetail(Long id) {
    var r = recipe(id);
    var result = new LinkedHashMap<String, Object>();
    result.put("recipe", r);
    result.put("lines", lines(id));
    result.put("revisions", revisions(id).stream().map(this::revisionView).toList());
    result.put("events", events("RECIPE", id));
    result.put(
        "snapshot",
        r.activeRevisionId == null
            ? null
            : snapshot(db.get(RecipeRevision.class, r.activeRevisionId)));
    return result;
  }

  /** 草稿或已批准版本按当前报价试算，不改写任何快照与状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public RecipeSnapshot preview(Long id) {
    var r = recipe(id);
    return build(r);
  }

  /** 批准只由指定且从未编辑的账号办理；所有阶段都检查旧版本与真实权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object recipeAction(Long id, String action, Map<String, Object> b) {
    write(Set.of("approve", "return").contains(action) ? "review" : "design");
    var r = recipe(id);
    version(r.version, b);
    String n = note(b);
    switch (action) {
      case "submit" -> {
        Rules.check(r.state.equals("DRAFT"), "STATE_INVALID");
        assigned(r.reviewerId, r.departmentId, "review", "REVIEWER_UNAVAILABLE");
        Rules.check(!edited(r.id, r.reviewerId), "INDEPENDENT_REQUIRED");
        Rules.check(revisions(id).size() < admin.setting("max_revisions"), "REVISION_LIMIT");
        var rev = new RecipeRevision();
        rev.recipeId = id;
        rev.number = revisions(id).size() + 1;
        rev.snapshotJson = encode(build(r));
        rev.digest = hash(rev.snapshotJson);
        rev.decision = "PENDING";
        rev.submittedBy = access.current().id;
        rev.createdAt = now();
        db.save(rev);
        r.activeRevisionId = rev.id;
        r.state = "PENDING";
      }
      case "approve", "return" -> {
        Rules.check(r.state.equals("PENDING"), "STATE_INVALID");
        Rules.check(r.reviewerId.equals(access.current().id), "REVIEWER_ONLY");
        Rules.check(!edited(id, access.current().id), "INDEPENDENT_REQUIRED");
        assigned(r.reviewerId, r.departmentId, "review", "REVIEWER_UNAVAILABLE");
        var rev = db.get(RecipeRevision.class, r.activeRevisionId);
        snapshot(rev);
        Rules.check(rev.decision.equals("PENDING"), "STATE_INVALID");
        if (action.equals("approve")) {
          for (var row : snapshot(rev).rows) {
            var i = db.get(Ingredient.class, row.ingredientId());
            Rules.check(i.enabled && i.version == row.priceVersion(), "PRICE_CHANGED");
          }
          rev.decision = "APPROVED";
          r.state = "APPROVED";
        } else {
          rev.decision = "RETURNED";
          r.state = "DRAFT";
          r.activeRevisionId = null;
        }
        rev.reviewedBy = access.current().id;
      }
      case "revise" -> {
        Rules.check(r.state.equals("APPROVED"), "STATE_INVALID");
        r.state = "DRAFT";
        r.activeRevisionId = null;
        editor(r);
      }
      case "archive" -> {
        Rules.check(Set.of("DRAFT", "APPROVED").contains(r.state), "STATE_INVALID");
        r.state = "ARCHIVED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    r.version++;
    event("RECIPE", id, r.departmentId, action.toUpperCase(Locale.ROOT), n);
    return recipeDetail(id);
  }

  /** 版本必须属于当前配方，数据范围不会被历史编号绕过。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> history(Long id, Long revision) {
    recipe(id);
    var rev = db.get(RecipeRevision.class, revision);
    Rules.check(rev.recipeId.equals(id), "REVISION_MISMATCH");
    return Map.of("revision", revisionView(rev), "snapshot", snapshot(rev));
  }

  /** 厨房执行列表按照账号实时范围过滤。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<PrepBatch> batches() {
    access.require("batches");
    return db.all(PrepBatch.class).stream().filter(this::visible).map(this::batchList).toList();
  }

  PrepBatch batchList(PrepBatch b) {
    var x = new PrepBatch();
    x.id = b.id;
    x.code = b.code;
    x.name = b.name;
    x.departmentId = b.departmentId;
    x.createdBy = b.createdBy;
    x.operatorId = b.operatorId;
    x.serviceDate = b.serviceDate;
    x.state = b.state;
    x.version = b.version;
    x.digest = b.digest;
    x.snapshotJson = "";
    return x;
  }

  /** 从当前批准版本生成冻结备料需求和按份数放大的子配方制作步骤。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  Preparation prepare(Long dept, Object input) {
    if (!(input instanceof List<?> list) || list.isEmpty() || list.size() > 20)
      throw new Problem(400, "LINE_LIMIT");
    Set<Long> seen = new HashSet<>();
    List<Dish> dishes = new ArrayList<>();
    List<CostEngine.Demand> demands = new ArrayList<>();
    for (var o : list) {
      if (!(o instanceof Map<?, ?> m)) throw new Problem(400, "INVALID_INPUT");
      Long id = Rules.id(m.get("revisionId"));
      Rules.check(seen.add(id), "DUPLICATE_DISH");
      var rev = db.get(RecipeRevision.class, id);
      var r = db.get(Recipe.class, rev.recipeId);
      Rules.check(
          r.departmentId.equals(dept)
              && r.state.equals("APPROVED")
              && r.activeRevisionId.equals(id)
              && rev.decision.equals("APPROVED"),
          "RECIPE_UNAVAILABLE");
      var s = snapshot(rev);
      for (var row : s.rows)
        Rules.check(db.get(Ingredient.class, row.ingredientId()).enabled, "INGREDIENT_UNAVAILABLE");
      int p = Rules.integer(m.get("portions"), 1, 100000);
      var componentTasks =
          s.components.stream()
              .map(
                  c ->
                      new Component(
                          c.recipeId,
                          c.revisionId,
                          c.portions
                              .multiply(new BigDecimal(p))
                              .divide(new BigDecimal(s.portions), 12, RoundingMode.HALF_UP),
                          c.name,
                          c.instructions))
              .toList();
      dishes.add(new Dish(id, p, s, componentTasks));
      demands.add(new CostEngine.Demand(s.rows, s.portions, p));
    }
    var requirements = CostEngine.prepare(demands);
    return new Preparation(
        List.copyOf(dishes),
        requirements,
        CostEngine.money(
            requirements.stream()
                .map(CostEngine.Requirement::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
  }

  /** 草稿需求从已批准配方重建；发布后不得改动，操作者必须具备执行权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveBatch(Long id, Map<String, Object> b) {
    write("plan");
    access.require("batches");
    var p = id == null ? new PrepBatch() : batch(id);
    if (id == null) {
      Rules.check(db.all(PrepBatch.class).size() < admin.setting("max_batches"), "RESOURCE_LIMIT");
      p.createdBy = access.current().id;
      p.state = "DRAFT";
    } else {
      version(p.version, b);
      Rules.check(p.state.equals("DRAFT"), "STATE_INVALID");
    }
    var dept = department(b.get("departmentId"));
    var c = code(b.get("code"));
    if (id != null) Rules.check(p.departmentId.equals(dept) && p.code.equals(c), "IDENTITY_LOCKED");
    p.code = c;
    p.name = Rules.text(b.get("name"), 120, true);
    p.departmentId = dept;
    p.operatorId = Rules.id(b.get("operatorId"));
    assigned(p.operatorId, dept, "execute", "OPERATOR_UNAVAILABLE");
    p.serviceDate = LocalDate.parse(Rules.text(b.get("serviceDate"), 10, true));
    Rules.check(
        p.serviceDate.getYear() >= 2000 && p.serviceDate.getYear() <= 2100, "INVALID_INPUT");
    p.snapshotJson = encode(prepare(dept, b.get("dishes")));
    p.digest = hash(p.snapshotJson);
    if (id == null) db.save(p);
    else p.version++;
    event("BATCH", p.id, p.departmentId, "SAVE", "");
    return batchDetail(p.id);
  }

  /** 执行详情不借用其他部门的目录，全部需求来自冻结快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> batchDetail(Long id) {
    var p = batch(id);
    var out = new LinkedHashMap<String, Object>();
    out.put("batch", batchList(p));
    out.put("preparation", preparation(p));
    out.put(
        "consumption",
        db.query(Consumption.class, "from Consumption where batchId=?1 order by ingredientId", id));
    out.put(
        "outputs",
        db.query(PrepOutput.class, "from PrepOutput where batchId=?1 order by revisionId", id));
    out.put("result", p.resultJson == null ? null : decode(p.resultJson, Result.class));
    out.put("events", events("BATCH", id));
    return out;
  }

  /** 发布前再次确认批准版本；开始与完工仅限被指定的执行人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object batchAction(Long id, String action, Map<String, Object> b) {
    write(Set.of("start", "complete").contains(action) ? "execute" : "plan");
    var p = batch(id);
    version(p.version, b);
    String n = note(b);
    var prep = preparation(p);
    switch (action) {
      case "release" -> {
        Rules.check(p.state.equals("DRAFT"), "STATE_INVALID");
        assigned(p.operatorId, p.departmentId, "execute", "OPERATOR_UNAVAILABLE");
        for (var dish : prep.dishes) {
          var r = db.get(Recipe.class, dish.recipe.recipeId);
          Rules.check(
              r.state.equals("APPROVED") && r.activeRevisionId.equals(dish.revisionId),
              "SNAPSHOT_STALE");
          for (var row : dish.recipe.rows)
            Rules.check(
                db.get(Ingredient.class, row.ingredientId()).enabled, "INGREDIENT_UNAVAILABLE");
        }
        p.state = "RELEASED";
      }
      case "start" -> {
        Rules.check(p.state.equals("RELEASED"), "STATE_INVALID");
        operator(p);
        p.state = "RUNNING";
      }
      case "complete" -> {
        Rules.check(p.state.equals("RUNNING"), "STATE_INVALID");
        operator(p);
        var actual =
            db.query(
                Consumption.class, "from Consumption where batchId=?1 order by ingredientId", id);
        var outputs =
            db.query(PrepOutput.class, "from PrepOutput where batchId=?1 order by revisionId", id);
        Rules.check(
            actual.size() == prep.requirements.size() && outputs.size() == prep.dishes.size(),
            "ACTUAL_INCOMPLETE");
        for (var x : actual)
          Rules.check(
              (x.quantity.signum() > 0 && x.waste.signum() == 0) || x.note.length() >= 4,
              "NOTE_REQUIRED");
        for (var x : outputs) {
          var dish =
              prep.dishes.stream()
                  .filter(d -> d.revisionId.equals(x.revisionId))
                  .findFirst()
                  .orElseThrow();
          Rules.check(x.portions == dish.portions || x.note.length() >= 4, "NOTE_REQUIRED");
        }
        List<Actual> rows = new ArrayList<>();
        for (var req : prep.requirements) {
          var x =
              actual.stream()
                  .filter(a -> a.ingredientId.equals(req.ingredientId()))
                  .findFirst()
                  .orElseThrow();
          var cost = CostEngine.money(x.quantity.multiply(req.weightedUnitCost()));
          rows.add(
              new Actual(
                  x.ingredientId,
                  x.quantity,
                  x.waste,
                  x.note,
                  cost,
                  x.quantity.subtract(req.raw()),
                  cost.subtract(CostEngine.money(req.cost()))));
        }
        var total =
            CostEngine.money(
                rows.stream().map(Actual::estimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add));
        p.resultJson =
            encode(
                new Result(
                    List.copyOf(rows),
                    outputs,
                    total,
                    total.subtract(prep.totalCost),
                    now().toString()));
        p.state = "COMPLETED";
      }
      case "cancel" -> {
        Rules.check(Set.of("DRAFT", "RELEASED").contains(p.state), "STATE_INVALID");
        p.state = "CANCELLED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    p.version++;
    event("BATCH", id, p.departmentId, action.toUpperCase(Locale.ROOT), n);
    return batchDetail(id);
  }

  /** 开始、录入与完工必须由仍具备执行权限的指定账号办理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void operator(PrepBatch b) {
    assigned(b.operatorId, b.departmentId, "execute", "OPERATOR_UNAVAILABLE");
    Rules.check(b.operatorId.equals(access.current().id), "OPERATOR_ONLY");
  }

  /** 一次原子保存全部实际行；服务端核对ID、单位、损耗、产出与版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object actual(Long id, Map<String, Object> b) {
    write("execute");
    var p = batch(id);
    version(p.version, b);
    Rules.check(p.state.equals("RUNNING"), "STATE_INVALID");
    operator(p);
    var prep = preparation(p);
    if (!(b.get("consumption") instanceof List<?> cs)
        || cs.size() != prep.requirements.size()
        || !(b.get("outputs") instanceof List<?> os)
        || os.size() != prep.dishes.size()) throw new Problem(400, "ACTUAL_INCOMPLETE");
    Set<Long> seen = new HashSet<>();
    List<Consumption> newRows = new ArrayList<>();
    for (var o : cs) {
      if (!(o instanceof Map<?, ?> m)) throw new Problem(400, "INVALID_INPUT");
      Long ingredient = Rules.id(m.get("ingredientId"));
      Rules.check(
          seen.add(ingredient)
              && prep.requirements.stream().anyMatch(x -> x.ingredientId().equals(ingredient)),
          "ACTUAL_INVALID");
      var x = new Consumption();
      x.batchId = id;
      x.ingredientId = ingredient;
      x.quantity =
          CostEngine.decimal(m.get("quantity"), 3, BigDecimal.ZERO, new BigDecimal("1000000000"));
      x.waste = CostEngine.decimal(m.get("waste"), 3, BigDecimal.ZERO, x.quantity);
      x.note = Rules.text(m.get("note"), 400, false);
      newRows.add(x);
    }
    Set<Long> dishes = new HashSet<>();
    List<PrepOutput> newOutputs = new ArrayList<>();
    for (var o : os) {
      if (!(o instanceof Map<?, ?> m)) throw new Problem(400, "INVALID_INPUT");
      Long rev = Rules.id(m.get("revisionId"));
      Rules.check(
          dishes.add(rev) && prep.dishes.stream().anyMatch(x -> x.revisionId.equals(rev)),
          "ACTUAL_INVALID");
      var x = new PrepOutput();
      x.batchId = id;
      x.revisionId = rev;
      x.portions = Rules.integer(m.get("portions"), 0, 100000);
      x.note = Rules.text(m.get("note"), 400, false);
      newOutputs.add(x);
    }
    for (var x : db.query(Consumption.class, "from Consumption where batchId=?1", id)) db.delete(x);
    for (var x : db.query(PrepOutput.class, "from PrepOutput where batchId=?1", id)) db.delete(x);
    newRows.forEach(db::save);
    newOutputs.forEach(db::save);
    p.version++;
    event("BATCH", id, p.departmentId, "ACTUAL_SAVE", "");
    return batchDetail(id);
  }

  /** 成本与完成统计只取当前可读业务，食材成本不包含人工、税费和能源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    access.require("reports");
    var recipes = access.has("recipes") ? recipes() : List.<Recipe>of();
    var batches = access.has("batches") ? batches() : List.<PrepBatch>of();
    var done =
        batches.stream()
            .filter(x -> x.state.equals("COMPLETED"))
            .map(x -> db.get(PrepBatch.class, x.id))
            .toList();
    BigDecimal planned = BigDecimal.ZERO, actual = BigDecimal.ZERO;
    int portions = 0;
    for (var b : done) {
      planned = planned.add(preparation(b).totalCost);
      var result = decode(b.resultJson, Result.class);
      actual = actual.add(result.estimatedActualCost);
      portions += result.outputs.stream().mapToInt(x -> x.portions).sum();
    }
    return Map.of(
        "recipes",
        recipes.size(),
        "approved",
        recipes.stream().filter(x -> x.state.equals("APPROVED")).count(),
        "batches",
        batches.size(),
        "completed",
        done.size(),
        "plannedCost",
        CostEngine.money(planned),
        "estimatedActualCost",
        CostEngine.money(actual),
        "costVariance",
        CostEngine.money(actual.subtract(planned)),
        "actualPortions",
        portions,
        "byState",
        batches.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    x -> x.state, java.util.stream.Collectors.counting())));
  }

  /** 审计按部门和本人过滤，权限不足不能读取账号目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<AuditEvent> audit() {
    access.require("audit");
    var a = access.current();
    return db.all(AuditEvent.class).stream()
        .filter(
            x ->
                access.department(x.departmentId)
                    && (!access.role().scope.equals("ASSIGNED") || x.actor.equals(a.username)))
        .sorted(Comparator.comparing((AuditEvent x) -> x.id).reversed())
        .toList();
  }

  /** CSV使用授权快照，名称安全转义并阻止表格公式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String recipeCsv(Long id, Long rev) {
    access.require("export");
    var h = history(id, rev);
    var s = (RecipeSnapshot) h.get("snapshot");
    StringBuilder out =
        new StringBuilder(
            "ingredient_code,name,base_unit,net,raw,yield_percent,unit_cost_cny,line_cost_cny\r\n");
    for (var row : s.rows)
      csvRow(
          out,
          row.code(),
          row.name(),
          row.unit(),
          row.net(),
          row.raw(),
          row.yieldPercent(),
          row.unitCost(),
          row.cost());
    return out.toString();
  }

  /** 备料导出不注入品牌，实际耗用仅在完工结果存在时展示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String batchCsv(Long id) {
    access.require("export");
    var b = batch(id);
    var p = preparation(b);
    var result = b.resultJson == null ? null : decode(b.resultJson, Result.class);
    StringBuilder out =
        new StringBuilder(
            "ingredient_code,name,base_unit,planned_net,planned_raw,planned_cost_cny,actual_raw,waste,estimated_actual_cost_cny\r\n");
    for (var row : p.requirements) {
      var actual =
          result == null
              ? null
              : result.consumption.stream()
                  .filter(x -> x.ingredientId.equals(row.ingredientId()))
                  .findFirst()
                  .orElseThrow();
      csvRow(
          out,
          row.code(),
          row.name(),
          row.unit(),
          row.net(),
          row.raw(),
          row.cost(),
          actual == null ? "" : actual.quantity,
          actual == null ? "" : actual.waste,
          actual == null ? "" : actual.estimatedCost);
    }
    return out.toString();
  }

  void csvRow(StringBuilder s, Object... row) {
    s.append(Arrays.stream(row).map(Rules::csv).collect(java.util.stream.Collectors.joining(",")))
        .append("\r\n");
  }
}
