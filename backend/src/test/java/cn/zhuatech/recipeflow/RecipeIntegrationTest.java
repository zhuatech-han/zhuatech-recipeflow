// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.recipeflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP、认证、事务、迁移、版本与隔离测试，全部TEST数据和随机私有凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RecipeIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:recipeflow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("recipeflow.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, author, reviewer, operator, reader;
  long authorId, reviewerId, operatorId, ingredientId, milkId, recipeId;
  String suffix;

  Map<String, Object> m(Object... v) {
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < v.length; i += 2) out.put(v[i].toString(), v[i + 1]);
    return out;
  }

  MockHttpServletRequestBuilder req(String p, String method, Map<String, Object> b) {
    var r =
        switch (method) {
          case "POST" -> post("/api" + p);
          case "PUT" -> put("/api" + p);
          case "DELETE" -> delete("/api" + p);
          default -> get("/api" + p);
        };
    if (b != null)
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    return r;
  }

  MvcResult result(MockHttpSession s, String p, String method, Map<String, Object> b)
      throws Exception {
    return mvc.perform(req(p, method, b).session(s)).andReturn();
  }

  JsonNode call(MockHttpSession s, String p, String method, Map<String, Object> b)
      throws Exception {
    var r = result(s, p, method, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(
      MockHttpSession s, String p, String method, Map<String, Object> b, int status, String code)
      throws Exception {
    var r = result(s, p, method, b);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).get("code").asString());
  }

  MockHttpSession login(String u) throws Exception {
    var r =
        mvc.perform(req("/auth/login", "POST", m("username", u, "password", PASSWORD))).andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession();
  }

  long user(String name, long role, long dept) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "password",
                PASSWORD,
                "roleId",
                role,
                "departmentId",
                dept,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  void eq(String expected, JsonNode n) {
    assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(n.asString())));
  }

  Map<String, Object> ingredient(
      String c, String name, String unit, String price, String yield, long dept) {
    return m(
        "code",
        c,
        "name",
        name,
        "unit",
        unit,
        "purchaseQuantity",
        "1",
        "purchasePrice",
        price,
        "yieldPercent",
        yield,
        "departmentId",
        dept,
        "category",
        "PRODUCE",
        "enabled",
        true);
  }

  Map<String, Object> recipeBody(long reviewer) {
    return m(
        "code",
        "R" + suffix,
        "name",
        "TEST carrot soup",
        "departmentId",
        1,
        "reviewerId",
        reviewer,
        "portions",
        5,
        "instructions",
        "TEST preparation instructions",
        "lines",
        List.of(
            m("ingredientId", ingredientId, "quantity", "500", "unit", "g"),
            m("ingredientId", milkId, "quantity", "0.2", "unit", "l")));
  }

  JsonNode detail() throws Exception {
    return call(author, "/recipes/" + recipeId, "GET", null);
  }

  long version() throws Exception {
    return detail().get("recipe").get("version").asLong();
  }

  long approve() throws Exception {
    call(
        author,
        "/recipes/" + recipeId + "/submit",
        "POST",
        m("version", version(), "note", "TEST submit"));
    return call(
            reviewer,
            "/recipes/" + recipeId + "/approve",
            "POST",
            m("version", version(), "note", "TEST approve"))
        .get("recipe")
        .get("activeRevisionId")
        .asLong();
  }

  JsonNode batch(long revision) throws Exception {
    return call(
        admin,
        "/batches",
        "POST",
        m(
            "code",
            "B" + suffix,
            "name",
            "TEST lunch prep",
            "departmentId",
            1,
            "operatorId",
            operatorId,
            "serviceDate",
            "2026-10-11",
            "dishes",
            List.of(m("revisionId", revision, "portions", 10))));
  }

  JsonNode batchAction(MockHttpSession s, long id, String action) throws Exception {
    var b = call(s, "/batches/" + id, "GET", null);
    return call(
        s,
        "/batches/" + id + "/" + action,
        "POST",
        m("version", b.get("batch").get("version").asLong(), "note", "TEST " + action));
  }

  Map<String, Object> actual(long rev, long v) {
    return m(
        "version",
        v,
        "consumption",
        List.of(
            m(
                "ingredientId",
                ingredientId,
                "quantity",
                "1300",
                "waste",
                "20",
                "note",
                "TEST prep trim"),
            m("ingredientId", milkId, "quantity", "420", "waste", "0", "note", "")),
        "outputs",
        List.of(m("revisionId", rev, "portions", 9, "note", "TEST one portion short")));
  }

  @BeforeEach
  void setup() throws Exception {
    suffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    admin = login("admin");
    authorId = user("author" + suffix.toLowerCase(), 2, 1);
    reviewerId = user("review" + suffix.toLowerCase(), 3, 1);
    operatorId = user("cook" + suffix.toLowerCase(), 5, 1);
    user("reader" + suffix.toLowerCase(), 6, 1);
    author = login("author" + suffix.toLowerCase());
    reviewer = login("review" + suffix.toLowerCase());
    operator = login("cook" + suffix.toLowerCase());
    reader = login("reader" + suffix.toLowerCase());
    ingredientId =
        call(
                author,
                "/ingredients",
                "POST",
                ingredient("C" + suffix, "TEST carrot", "kg", "12", "80", 1))
            .get("id")
            .asLong();
    milkId =
        call(
                author,
                "/ingredients",
                "POST",
                ingredient("M" + suffix, "TEST milk", "l", "8", "100", 1))
            .get("id")
            .asLong();
    recipeId =
        call(author, "/recipes", "POST", recipeBody(reviewerId)).get("recipe").get("id").asLong();
  }

  @Test
  void fullApprovedRecipeToCompletedKitchenBatch() throws Exception {
    eq("9.10", call(author, "/recipes/" + recipeId + "/preview", "GET", null).get("totalCost"));
    long rev = approve();
    var b = batch(rev);
    long id = b.get("batch").get("id").asLong();
    eq("18.20", b.get("preparation").get("totalCost"));
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    var current = call(operator, "/batches/" + id, "GET", null);
    call(
        operator,
        "/batches/" + id + "/actual",
        "POST",
        actual(rev, current.get("batch").get("version").asLong()));
    var done = batchAction(operator, id, "complete");
    assertEquals("COMPLETED", done.get("batch").get("state").asString());
    eq("18.96", done.get("result").get("estimatedActualCost"));
    eq("0.76", done.get("result").get("costVariance"));
    assertEquals(9, done.get("result").get("outputs").get(0).get("portions").asInt());
  }

  @Test
  void unauthorizedAnonymousReadAndMissingCsrfDenied() throws Exception {
    assertEquals(401, mvc.perform(get("/api/recipes")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/ingredients")
                    .session(admin)
                    .contentType("application/json")
                    .content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void readonlyIngredientRoleCannotChangeQuote() throws Exception {
    call(reader, "/ingredients", "GET", null);
    fail(
        reader,
        "/ingredients",
        "POST",
        ingredient("XX" + suffix, "TEST", "kg", "12", "80", 1),
        403,
        "FORBIDDEN");
  }

  @Test
  void unauthorizedDirectoryDoesNotExposePasswordHashes() throws Exception {
    fail(author, "/admin/users", "GET", null, 403, "FORBIDDEN");
    String users = result(admin, "/admin/users", "GET", null).getResponse().getContentAsString();
    assertFalse(users.contains("passwordHash"));
    assertFalse(users.contains(PASSWORD));
  }

  @Test
  void staleDraftCannotOverwriteAndNoPartialLinesSaved() throws Exception {
    var body = recipeBody(reviewerId);
    body.put("version", 1);
    call(author, "/recipes/" + recipeId, "PUT", body);
    body.put("name", "TEST stale");
    fail(author, "/recipes/" + recipeId, "PUT", body, 409, "VERSION_CONFLICT");
    assertEquals("TEST carrot soup", detail().get("recipe").get("name").asString());
  }

  @Test
  void wrongDimensionsRollbackWholeRecipeUpdate() throws Exception {
    var body = recipeBody(reviewerId);
    body.put("version", version());
    body.put("lines", List.of(m("ingredientId", ingredientId, "quantity", "500", "unit", "ml")));
    fail(author, "/recipes/" + recipeId, "PUT", body, 409, "UNIT_MISMATCH");
    assertEquals(2, detail().get("lines").size());
  }

  @Test
  void invalidDecimalQuoteIsRejected() throws Exception {
    var body = ingredient("XX" + suffix, "TEST", "kg", "1.00001", "80", 1);
    fail(author, "/ingredients", "POST", body, 400, "INVALID_DECIMAL");
  }

  @Test
  void onlyAssignedIndependentReviewerCanApprove() throws Exception {
    call(
        author,
        "/recipes/" + recipeId + "/submit",
        "POST",
        m("version", version(), "note", "TEST submit"));
    fail(
        admin,
        "/recipes/" + recipeId + "/approve",
        "POST",
        m("version", version(), "note", "TEST improper"),
        409,
        "REVIEWER_ONLY");
    call(
        reviewer,
        "/recipes/" + recipeId + "/approve",
        "POST",
        m("version", version(), "note", "TEST approve"));
  }

  @Test
  void historicalEditorCannotBecomeIndependentReviewer() throws Exception {
    var role = call(admin, "/admin/roles", "GET", null).get(1);
    var codes = new ArrayList<String>();
    for (var c : role.get("permissions")) codes.add(c.asString());
    codes.add("review");
    call(
        admin,
        "/admin/roles/2",
        "PUT",
        m(
            "version",
            role.get("version").asLong(),
            "name",
            role.get("name").asString(),
            "scope",
            "DEPARTMENT",
            "permissions",
            codes));
    try {
      var body = recipeBody(authorId);
      body.put("version", version());
      call(author, "/recipes/" + recipeId, "PUT", body);
      fail(
          author,
          "/recipes/" + recipeId + "/submit",
          "POST",
          m("version", version(), "note", "TEST submit"),
          409,
          "INDEPENDENT_REQUIRED");
    } finally {
      codes.remove("review");
      var latest = call(admin, "/admin/roles", "GET", null).get(1);
      call(
          admin,
          "/admin/roles/2",
          "PUT",
          m(
              "version",
              latest.get("version").asLong(),
              "name",
              latest.get("name").asString(),
              "scope",
              "DEPARTMENT",
              "permissions",
              codes));
    }
  }

  @Test
  void quoteChangedDuringReviewRequiresReturnAndResubmit() throws Exception {
    call(
        author,
        "/recipes/" + recipeId + "/submit",
        "POST",
        m("version", version(), "note", "TEST submit"));
    var body = ingredient("C" + suffix, "TEST carrot", "kg", "24", "80", 1);
    body.put("version", 1);
    call(author, "/ingredients/" + ingredientId, "PUT", body);
    fail(
        reviewer,
        "/recipes/" + recipeId + "/approve",
        "POST",
        m("version", version(), "note", "TEST approve"),
        409,
        "PRICE_CHANGED");
    call(
        reviewer,
        "/recipes/" + recipeId + "/return",
        "POST",
        m("version", version(), "note", "TEST quote change"));
    var rev = approve();
    eq(
        "16.60",
        call(author, "/recipes/" + recipeId + "/revisions/" + rev, "GET", null)
            .get("snapshot")
            .get("totalCost"));
  }

  @Test
  void laterPriceUpdateDoesNotRewriteApprovedOrReleasedSnapshots() throws Exception {
    long rev = approve();
    var b = batch(rev);
    long id = b.get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    String before =
        call(author, "/recipes/" + recipeId + "/revisions/" + rev, "GET", null)
            .get("snapshot")
            .toString();
    var body = ingredient("C" + suffix, "TEST carrot", "kg", "24", "80", 1);
    body.put("version", 1);
    call(author, "/ingredients/" + ingredientId, "PUT", body);
    eq("16.60", call(author, "/recipes/" + recipeId + "/preview", "GET", null).get("totalCost"));
    assertEquals(
        before,
        call(author, "/recipes/" + recipeId + "/revisions/" + rev, "GET", null)
            .get("snapshot")
            .toString());
    eq("18.20", call(admin, "/batches/" + id, "GET", null).get("preparation").get("totalCost"));
  }

  @Test
  void approvedRecipeCannotBeEditedInPlace() throws Exception {
    approve();
    var body = recipeBody(reviewerId);
    body.put("version", version());
    fail(author, "/recipes/" + recipeId, "PUT", body, 409, "STATE_INVALID");
  }

  @Test
  void draftBatchMustRefreshWhenApprovedRecipeRevised() throws Exception {
    long rev = approve();
    long id = batch(rev).get("batch").get("id").asLong();
    call(
        author,
        "/recipes/" + recipeId + "/revise",
        "POST",
        m("version", version(), "note", "TEST new recipe"));
    fail(
        admin,
        "/batches/" + id + "/release",
        "POST",
        m("version", 1, "note", "TEST release"),
        409,
        "SNAPSHOT_STALE");
  }

  @Test
  void onlyAssignedOperatorCanExecuteEvenAdministrator() throws Exception {
    long id = batch(approve()).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    fail(
        admin,
        "/batches/" + id + "/start",
        "POST",
        m("version", 2, "note", "TEST start"),
        409,
        "OPERATOR_ONLY");
    batchAction(operator, id, "start");
  }

  @Test
  void completionRequiresEveryActualRow() throws Exception {
    long id = batch(approve()).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    fail(
        operator,
        "/batches/" + id + "/complete",
        "POST",
        m("version", 3, "note", "TEST complete"),
        409,
        "ACTUAL_INCOMPLETE");
  }

  @Test
  void wasteGreaterThanConsumptionRejectedAtomically() throws Exception {
    long rev = approve(), id = batch(rev).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    var body = actual(rev, 3);
    body.put(
        "consumption",
        List.of(
            m("ingredientId", ingredientId, "quantity", "1", "waste", "2", "note", "TEST excess"),
            m("ingredientId", milkId, "quantity", "420", "waste", "0", "note", "")));
    fail(operator, "/batches/" + id + "/actual", "POST", body, 400, "INVALID_DECIMAL");
    assertEquals(0, call(operator, "/batches/" + id, "GET", null).get("consumption").size());
  }

  @Test
  void completedResultRejectsFurtherChanges() throws Exception {
    long rev = approve(), id = batch(rev).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    call(operator, "/batches/" + id + "/actual", "POST", actual(rev, 3));
    var done = batchAction(operator, id, "complete");
    fail(
        operator,
        "/batches/" + id + "/actual",
        "POST",
        actual(rev, done.get("batch").get("version").asLong()),
        409,
        "STATE_INVALID");
  }

  @Test
  void runningBatchCannotBeCancelled() throws Exception {
    long id = batch(approve()).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    fail(
        admin,
        "/batches/" + id + "/cancel",
        "POST",
        m("version", 3, "note", "TEST cancellation"),
        409,
        "STATE_INVALID");
  }

  @Test
  void differentKitchenCannotReadOrSelectRecords() throws Exception {
    long dept =
        call(
                admin,
                "/admin/departments",
                "POST",
                m("name", "TEST other kitchen " + suffix, "zone", "UTC", "enabled", true))
            .get("id")
            .asLong();
    user("other" + suffix.toLowerCase(), 2, dept);
    var other = login("other" + suffix.toLowerCase());
    fail(other, "/recipes/" + recipeId, "GET", null, 403, "OUT_OF_SCOPE");
    for (var row : call(other, "/ingredients", "GET", null))
      assertNotEquals(ingredientId, row.get("id").asLong());
    for (var row : call(other, "/options", "GET", null).get("published"))
      assertNotEquals(recipeId, row.get("id").asLong());
  }

  @Test
  void assignedReviewerDoesNotSeeOtherAssignments() throws Exception {
    user("otherreview" + suffix.toLowerCase(), 3, 1);
    var other = login("otherreview" + suffix.toLowerCase());
    fail(other, "/recipes/" + recipeId, "GET", null, 403, "OUT_OF_SCOPE");
  }

  @Test
  void nestedRecipeUsesPinnedQuantitiesAndCurrentQuoteAtSubmit() throws Exception {
    long rev = approve();
    var body =
        m(
            "code",
            "SUB" + suffix,
            "name",
            "TEST nested dish",
            "departmentId",
            1,
            "reviewerId",
            reviewerId,
            "portions",
            2,
            "instructions",
            "TEST use soup base",
            "lines",
            List.of(m("componentRevisionId", rev, "quantity", "2.5", "unit", "portion")));
    var created = call(author, "/recipes", "POST", body);
    long nested = created.get("recipe").get("id").asLong();
    var p = call(author, "/recipes/" + nested + "/preview", "GET", null);
    eq("4.55", p.get("totalCost"));
    eq("250", p.get("rows").get(0).get("net"));
    assertEquals(2, p.get("depth").asInt());
    assertEquals(
        "TEST preparation instructions", p.get("components").get(0).get("instructions").asString());
    eq("2.5", p.get("components").get(0).get("portions"));
  }

  @Test
  void crossRevisionRecipeCycleRejected() throws Exception {
    long first = approve();
    var body =
        m(
            "code",
            "SUB" + suffix,
            "name",
            "TEST nested dish",
            "departmentId",
            1,
            "reviewerId",
            reviewerId,
            "portions",
            2,
            "instructions",
            "TEST use soup base",
            "lines",
            List.of(m("componentRevisionId", first, "quantity", "2", "unit", "portion")));
    var c = call(author, "/recipes", "POST", body);
    long nested = c.get("recipe").get("id").asLong();
    call(
        author,
        "/recipes/" + nested + "/submit",
        "POST",
        m("version", 1, "note", "TEST nested submit"));
    var second =
        call(
                reviewer,
                "/recipes/" + nested + "/approve",
                "POST",
                m("version", 2, "note", "TEST nested approve"))
            .get("recipe")
            .get("activeRevisionId")
            .asLong();
    call(
        author,
        "/recipes/" + recipeId + "/revise",
        "POST",
        m("version", version(), "note", "TEST revise"));
    var original = recipeBody(reviewerId);
    original.put("version", version());
    original.put(
        "lines", List.of(m("componentRevisionId", second, "quantity", "1", "unit", "portion")));
    fail(author, "/recipes/" + recipeId, "PUT", original, 409, "RECIPE_CYCLE");
  }

  @Test
  void historicalRevisionOwnershipIsChecked() throws Exception {
    long rev = approve();
    var body = recipeBody(reviewerId);
    body.put("code", "OTHER" + suffix);
    long other = call(author, "/recipes", "POST", body).get("recipe").get("id").asLong();
    fail(author, "/recipes/" + other + "/revisions/" + rev, "GET", null, 409, "REVISION_MISMATCH");
  }

  @Test
  void referencedIngredientDeletionDenied() throws Exception {
    fail(author, "/ingredients/" + ingredientId, "DELETE", m("version", 1), 409, "REFERENCED");
  }

  @Test
  void noLastAdministratorRemoval() throws Exception {
    var a = call(admin, "/admin/users", "GET", null).get(0);
    fail(
        admin,
        "/admin/users/1",
        "PUT",
        m(
            "version",
            a.get("version").asLong(),
            "username",
            "admin",
            "displayName",
            "TEST admin",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            false,
            "password",
            ""),
        409,
        "LAST_ADMIN");
  }

  @Test
  void disabledAccountRevokesExistingSession() throws Exception {
    var users = call(admin, "/admin/users", "GET", null);
    JsonNode a = null;
    for (var u : users) if (u.get("id").asLong() == operatorId) a = u;
    assertNotNull(a);
    call(
        admin,
        "/admin/users/" + operatorId,
        "PUT",
        m(
            "version",
            a.get("version").asLong(),
            "username",
            a.get("username").asString(),
            "displayName",
            a.get("displayName").asString(),
            "roleId",
            5,
            "departmentId",
            1,
            "enabled",
            false,
            "password",
            ""));
    fail(operator, "/auth/me", "GET", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void selfAdminPasswordResetCommitsAuditAndRevokesOldSession() throws Exception {
    String username = "selfadmin" + suffix.toLowerCase();
    long id = user(username, 1, 1);
    var session = login(username);
    String next = "Bb8" + UUID.randomUUID();
    call(
        session,
        "/admin/users/" + id,
        "PUT",
        m(
            "version",
            1,
            "username",
            username,
            "displayName",
            "TEST self admin",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            true,
            "password",
            next));
    fail(session, "/auth/me", "GET", null, 401, "UNAUTHENTICATED");
    var loginResult =
        mvc.perform(req("/auth/login", "POST", m("username", username, "password", next)))
            .andReturn();
    assertEquals(200, loginResult.getResponse().getStatus());
    // 清理测试新增管理员，避免影响最后管理员测试的前置条件。
    call(
        admin,
        "/admin/users/" + id,
        "PUT",
        m(
            "version",
            2,
            "username",
            username,
            "displayName",
            "TEST self admin",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            false,
            "password",
            ""));
  }

  @Test
  void selfPasswordChangeAdvancesAccountVersionAndLogsOut() throws Exception {
    var response =
        call(
            author,
            "/auth/password",
            "POST",
            m("oldPassword", PASSWORD, "newPassword", "Cc7" + UUID.randomUUID()));
    assertTrue(response.get("ok").asBoolean());
    JsonNode account = null;
    for (var x : call(admin, "/admin/users", "GET", null))
      if (x.get("id").asLong() == authorId) account = x;
    assertNotNull(account);
    assertEquals(2, account.get("version").asLong());
  }

  @Test
  void zeroConsumptionNeedsAnExplanationBeforeCompletion() throws Exception {
    long rev = approve(), id = batch(rev).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    var body = actual(rev, 3);
    body.put(
        "consumption",
        List.of(
            m("ingredientId", ingredientId, "quantity", "0", "waste", "0", "note", ""),
            m("ingredientId", milkId, "quantity", "420", "waste", "0", "note", "")));
    call(operator, "/batches/" + id + "/actual", "POST", body);
    fail(
        operator,
        "/batches/" + id + "/complete",
        "POST",
        m("version", 4, "note", "TEST completion"),
        409,
        "NOTE_REQUIRED");
  }

  @Test
  void httpDecimalPrecisionIsValidatedBeforeFloatingRounding() throws Exception {
    var body = ingredient("PRECISION" + suffix, "TEST precision", "kg", "1", "100", 1);
    body.put("purchasePrice", new BigDecimal("1.00000000000000001"));
    fail(author, "/ingredients", "POST", body, 400, "INVALID_DECIMAL");
  }

  @Test
  void httpLargeActualQuantityRetainsThreeDecimalPlaces() throws Exception {
    long rev = approve(), id = batch(rev).get("batch").get("id").asLong();
    batchAction(admin, id, "release");
    batchAction(operator, id, "start");
    var body = actual(rev, 3);
    body.put(
        "consumption",
        List.of(
            m(
                "ingredientId",
                ingredientId,
                "quantity",
                new BigDecimal("10000000.001"),
                "waste",
                "0",
                "note",
                "TEST numeric range"),
            m("ingredientId", milkId, "quantity", "420", "waste", "0", "note", "")));
    var response = call(operator, "/batches/" + id + "/actual", "POST", body);
    eq("10000000.001", response.get("consumption").get(0).get("quantity"));
  }

  @Test
  void multilineInstructionsRemainInApprovedSnapshot() throws Exception {
    var body = recipeBody(reviewerId);
    body.put("version", version());
    body.put("instructions", "TEST 第一步清洗\nTEST 第二步制作");
    call(author, "/recipes/" + recipeId, "PUT", body);
    long revision = approve();
    assertEquals(
        "TEST 第一步清洗\nTEST 第二步制作",
        call(author, "/recipes/" + recipeId + "/revisions/" + revision, "GET", null)
            .get("snapshot")
            .get("instructions")
            .asString());
  }
}
