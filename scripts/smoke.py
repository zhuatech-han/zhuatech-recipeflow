#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""隔离MySQL实例的真实业务与恢复检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import concurrent.futures
import hashlib
import http.cookiejar
import json
import os
import secrets
import urllib.error
import urllib.request
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKS = 0


def check(ok, message):
    global CHECKS
    CHECKS += 1
    if not ok:
        raise AssertionError(message)


class Client:
    """正常HTTP会话，不复用浏览器或真实账号凭据。"""

    def __init__(self, base):
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        self.token = None

    def request(self, path, method="GET", body=None, status=200, csrf=True):
        headers = {}
        if method not in ("GET", "HEAD") and csrf:
            if self.token is None:
                self.token = self.request("/api/auth/csrf")
            headers[self.token["header"]] = self.token["token"]
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            self.base + path, data=data, method=method, headers=headers
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                code, content = response.status, response.read()
        except urllib.error.HTTPError as error:
            code, content = error.code, error.read()
        check(
            code == status,
            f"{method} {path}: expected {status}, got {code}; credentials omitted",
        )
        if method == "HEAD":
            return None
        try:
            return json.loads(content)
        except (ValueError, UnicodeDecodeError):
            return content.decode()

    def login(self, username, password):
        profile = self.request(
            "/api/auth/login", "POST", {"username": username, "password": password}
        )
        self.token = None
        return profile


def private_write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def env(path=ROOT / ".env"):
    return dict(
        line.split("=", 1)
        for line in Path(path).read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )


def clients(base, state):
    out = {}
    for name, account in state["accounts"].items():
        c = Client(base)
        c.login(account["username"], account["password"])
        out[name] = c
    return out


def seed(base, path, env_file):
    if Path(path).exists():
        raise SystemExit("Refuse to overwrite private test state")
    config = env(env_file)
    state = {
        "accounts": {
            "admin": {
                "username": config.get("ADMIN_USERNAME", "admin"),
                "password": config["ADMIN_PASSWORD"],
            }
        },
        "ids": {},
    }
    admin = Client(base)
    admin.login(
        state["accounts"]["admin"]["username"], state["accounts"]["admin"]["password"]
    )
    check(admin.request("/actuator/health")["status"] == "UP", "Backend health")
    check(
        admin.request("/api/recipes") == [], "Isolated test database must begin empty"
    )
    token = secrets.token_hex(4)
    other = admin.request(
        "/api/admin/departments",
        "POST",
        {"name": "TEST 独立厨房 / Other kitchen", "zone": "UTC", "enabled": True},
    )
    for name, role, dept, label in [
        ("author", 2, 1, "TEST 配方研发"),
        ("reviewer", 3, 1, "TEST 独立审核"),
        ("planner", 4, 1, "TEST 备料计划"),
        ("cook", 5, 1, "TEST 厨房执行"),
        ("reader", 6, 1, "TEST 部门查阅"),
        ("outsider", 2, other["id"], "TEST 外部厨房"),
    ]:
        username = name + token
        password = "Aa9" + secrets.token_hex(20)
        account = admin.request(
            "/api/admin/users",
            "POST",
            {
                "username": username,
                "displayName": label,
                "password": password,
                "roleId": role,
                "departmentId": dept,
                "enabled": True,
            },
        )
        state["accounts"][name] = {
            "username": username,
            "password": password,
            "id": account["id"],
        }
    c = clients(base, state)
    author, reviewer, planner, cook = (
        c[x] for x in ["author", "reviewer", "planner", "cook"]
    )
    ingredient_ids = []
    for code, name, unit, price, yield_value in [
        ("CARROT", "TEST 胡萝卜", "kg", "12", "80"),
        ("MILK", "TEST 牛奶", "l", "8", "100"),
    ]:
        x = author.request(
            "/api/ingredients",
            "POST",
            {
                "code": code,
                "name": name,
                "unit": unit,
                "purchaseQuantity": "1",
                "purchasePrice": price,
                "yieldPercent": yield_value,
                "category": "PRODUCE",
                "departmentId": 1,
                "enabled": True,
            },
        )
        ingredient_ids.append(x["id"])
    state["ids"]["ingredients"] = ingredient_ids

    def recipe(code, name, lines, portions=5):
        return author.request(
            "/api/recipes",
            "POST",
            {
                "code": code,
                "name": name,
                "departmentId": 1,
                "reviewerId": state["accounts"]["reviewer"]["id"],
                "portions": portions,
                "instructions": "TEST 第一步清洗处理食材。\nTEST 第二步按厨房核定流程分批制作；此说明不是食品安全标准。",
                "lines": lines,
            },
        )

    def decision(row, action, client):
        return client.request(
            f"/api/recipes/{row['recipe']['id']}/{action}",
            "POST",
            {"version": row["recipe"]["version"], "note": "TEST " + action + " record"},
        )

    first = recipe(
        "SOUP",
        "TEST 胡萝卜浓汤",
        [
            {"ingredientId": ingredient_ids[0], "quantity": "500", "unit": "g"},
            {"ingredientId": ingredient_ids[1], "quantity": "0.2", "unit": "l"},
        ],
    )
    recipe_id = first["recipe"]["id"]
    check(
        Decimal(str(author.request(f"/api/recipes/{recipe_id}/preview")["totalCost"]))
        == Decimal("9.10"),
        "Independent soup cost oracle",
    )
    first = decision(first, "submit", author)
    denied = admin.request(
        f"/api/recipes/{recipe_id}/approve",
        "POST",
        {"version": first["recipe"]["version"], "note": "TEST wrong reviewer"},
        409,
    )
    check(
        denied["code"] == "REVIEWER_ONLY",
        "Administrator cannot replace designated reviewer",
    )
    first = decision(first, "approve", reviewer)
    revision = first["recipe"]["activeRevisionId"]
    second = recipe(
        "PORTION",
        "TEST 小份浓汤",
        [{"componentRevisionId": revision, "quantity": "2.5", "unit": "portion"}],
        2,
    )
    second = decision(decision(second, "submit", author), "approve", reviewer)
    pending = recipe(
        "DAILY",
        "TEST 每日浓汤修订审核",
        [
            {"ingredientId": ingredient_ids[0], "quantity": "400", "unit": "g"},
            {"ingredientId": ingredient_ids[1], "quantity": "160", "unit": "ml"},
        ],
        4,
    )
    pending = decision(pending, "submit", author)
    state["ids"].update(
        recipe=recipe_id,
        revision=revision,
        component=second["recipe"]["id"],
        componentRevision=second["recipe"]["activeRevisionId"],
        pendingRecipe=pending["recipe"]["id"],
    )

    def batch(code, name, portions):
        return planner.request(
            "/api/batches",
            "POST",
            {
                "code": code,
                "name": name,
                "departmentId": 1,
                "operatorId": state["accounts"]["cook"]["id"],
                "serviceDate": "2026-10-11",
                "dishes": [{"revisionId": revision, "portions": portions}],
            },
        )

    def action(row, name, client):
        return client.request(
            f"/api/batches/{row['batch']['id']}/{name}",
            "POST",
            {"version": row["batch"]["version"], "note": "TEST " + name + " record"},
        )

    b = batch("LUNCH", "TEST 午餐备料与成本核验", 10)
    check(
        Decimal(str(b["preparation"]["totalCost"])) == Decimal("18.20"),
        "Independent batch cost oracle",
    )
    b = action(action(b, "release", planner), "start", cook)
    b = cook.request(
        f"/api/batches/{b['batch']['id']}/actual",
        "POST",
        {
            "version": b["batch"]["version"],
            "consumption": [
                {
                    "ingredientId": ingredient_ids[0],
                    "quantity": "1300",
                    "waste": "20",
                    "note": "TEST 清洗修整耗用",
                },
                {
                    "ingredientId": ingredient_ids[1],
                    "quantity": "420",
                    "waste": "0",
                    "note": "",
                },
            ],
            "outputs": [
                {"revisionId": revision, "portions": 9, "note": "TEST 实际少产一份"}
            ],
        },
    )
    b = action(b, "complete", cook)
    check(
        Decimal(str(b["result"]["estimatedActualCost"])) == Decimal("18.96"),
        "Actual value oracle",
    )
    check(
        Decimal(str(b["result"]["costVariance"])) == Decimal("0.76"),
        "Cost variance oracle",
    )
    state["ids"]["completedBatch"] = b["batch"]["id"]
    gui = action(batch("DINNER", "TEST 晚餐厨房执行", 20), "release", planner)
    state["ids"]["guiBatch"] = gui["batch"]["id"]
    # Later price changes must leave both old recipe and released batch intact.
    quote = author.request("/api/ingredients")[0]
    quote["purchasePrice"] = "24"
    author.request(f"/api/ingredients/{quote['id']}", "PUT", quote)
    frozen = author.request(f"/api/recipes/{recipe_id}/revisions/{revision}")
    check(
        Decimal(str(frozen["snapshot"]["totalCost"])) == Decimal("9.10"),
        "Old approved cost preserved",
    )
    check(
        Decimal(str(author.request(f"/api/recipes/{recipe_id}/preview")["totalCost"]))
        == Decimal("16.60"),
        "Current quote comparison",
    )
    check(
        Decimal(
            str(
                planner.request(f"/api/batches/{gui['batch']['id']}")["preparation"][
                    "totalCost"
                ]
            )
        )
        == Decimal("36.40"),
        "Released demand retains old prices",
    )
    # Return the other pending recipe after its quote changes; it stays a GUI review task.
    pending = decision(pending, "return", reviewer)
    pending = decision(pending, "submit", author)
    # Two real simultaneous sessions attempt the same quote version.
    current = author.request("/api/ingredients")[0]

    def race():
        client = Client(base)
        client.login(
            state["accounts"]["author"]["username"],
            state["accounts"]["author"]["password"],
        )
        body = dict(current)
        body["purchasePrice"] = "24"
        try:
            client.request(f"/api/ingredients/{current['id']}", "PUT", body)
            return 200
        except AssertionError as e:
            if "got 409" in str(e):
                return 409
            raise

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        statuses = sorted(pool.map(lambda _: race(), range(2)))
    check(statuses == [200, 409], "Concurrent overwrite rejected")
    # Quote version changed in race; resubmit the pending GUI task.
    pending = decision(pending, "return", reviewer)
    decision(pending, "submit", author)
    private_write(path, state)
    verify(base, state)
    print(
        json.dumps(
            {
                "mode": "seed",
                "checks": CHECKS,
                "result": "passed",
                "privateState": "created; credentials omitted",
            },
            ensure_ascii=False,
        )
    )


def verify(base, state):
    c = clients(base, state)
    ids = state["ids"]
    anon = Client(base)
    check(anon.request("/actuator/health")["status"] == "UP", "health")
    anon.request("/api/recipes", status=401)
    c["reader"].request("/api/ingredients", "POST", {}, 403)
    c["author"].request("/api/admin/users", status=403)
    c["outsider"].request("/api/recipes/" + str(ids["recipe"]), status=403)
    c["cook"].request("/api/recipes", status=403)
    c["planner"].request("/api/batches", "POST", {}, 403, csrf=False)
    hist = c["author"].request(
        f"/api/recipes/{ids['recipe']}/revisions/{ids['revision']}"
    )
    check(
        Decimal(str(hist["snapshot"]["totalCost"])) == Decimal("9.10"),
        "Frozen recipe price",
    )
    done = c["cook"].request("/api/batches/" + str(ids["completedBatch"]))
    check(done["batch"]["state"] == "COMPLETED", "completed state")
    check(
        Decimal(str(done["result"]["estimatedActualCost"])) == Decimal("18.96"),
        "Frozen completion valuation",
    )
    check(
        Decimal(str(done["result"]["costVariance"])) == Decimal("0.76"),
        "Frozen completion variance",
    )
    check(done["result"]["outputs"][0]["portions"] == 9, "Actual portions")
    c["cook"].request(
        f"/api/batches/{ids['completedBatch']}/complete",
        "POST",
        {"version": done["batch"]["version"], "note": "TEST repeated completion"},
        409,
    )
    c["outsider"].request("/api/batches/" + str(ids["completedBatch"]), status=403)
    c["cook"].request(
        "/api/batches/" + str(ids["completedBatch"]) + "/export.csv", method="HEAD"
    )
    c["outsider"].request(
        "/api/batches/" + str(ids["completedBatch"]) + "/export.csv",
        method="HEAD",
        status=403,
    )
    csv = c["cook"].request(
        "/api/batches/" + str(ids["completedBatch"]) + "/export.csv"
    )
    check(
        "TEST 胡萝卜" in csv and "18.96" not in csv,
        "CSV includes actual ingredient rows, not invented totals",
    )
    audit = c["admin"].request("/api/audit")
    check(
        all("password" not in e and "token" not in e for e in audit),
        "Audit excludes secret fields",
    )
    users = c["admin"].request("/api/admin/users")
    check(all("passwordHash" not in u for u in users), "No exposed password hashes")
    options = c["outsider"].request("/api/options")
    check(
        all(x["departmentId"] != 1 for x in options["ingredients"]),
        "Other-kitchen ingredient scope",
    )
    return c


def capture(base, state):
    c = verify(base, state)
    admin = c["admin"]
    out = {}
    for kind in [
        "users",
        "roles",
        "permissions",
        "departments",
        "menus",
        "dictionaries",
        "settings",
    ]:
        out["admin/" + kind] = admin.request("/api/admin/" + kind)
    recipes = admin.request("/api/recipes")
    out["recipes"] = recipes
    for recipe in recipes:
        rid = recipe["id"]
        out[f"recipes/{rid}"] = admin.request(f"/api/recipes/{rid}")
        for rev in out[f"recipes/{rid}"]["revisions"]:
            out[f'revisions/{rev["id"]}'] = admin.request(
                f'/api/recipes/{rid}/revisions/{rev["id"]}'
            )
            out[f'recipe-csv/{rev["id"]}'] = admin.request(
                f'/api/recipes/{rid}/revisions/{rev["id"]}/export.csv'
            )
    out["ingredients"] = admin.request("/api/ingredients")
    batches = admin.request("/api/batches")
    out["batches"] = batches
    for batch in batches:
        bid = batch["id"]
        out[f"batches/{bid}"] = admin.request(f"/api/batches/{bid}")
        out[f"batch-csv/{bid}"] = admin.request(f"/api/batches/{bid}/export.csv")
    out["reports"] = admin.request("/api/reports")
    out["audit"] = [x for x in admin.request("/api/audit") if x["action"] != "LOGIN"]
    return out


def main():
    p = argparse.ArgumentParser(
        description="Only use an isolated localhost QA database; creates TEST accounts and business records."
    )
    p.add_argument("--base", default="http://127.0.0.1:19134")
    p.add_argument(
        "--env-file",
        default=str(ROOT / ".env"),
        help="Private isolated test configuration",
    )
    p.add_argument(
        "--state", required=True, help="Private generated credentials; never commit"
    )
    p.add_argument(
        "--mode", choices=["seed", "check", "capture", "compare"], default="check"
    )
    p.add_argument("--snapshot", help="Private baseline JSON for capture/compare")
    a = p.parse_args()
    if not a.base.startswith(("http://127.0.0.1:", "http://localhost:")):
        p.error("QA checks are restricted to isolated localhost instances")
    if a.mode == "seed":
        seed(a.base, a.state, a.env_file)
        return
    state = json.loads(Path(a.state).read_text())
    if a.mode == "check":
        verify(a.base, state)
    else:
        if not a.snapshot:
            p.error("--snapshot is required")
        current = capture(a.base, state)
        if a.mode == "capture":
            private_write(a.snapshot, current)
        else:
            expected = json.loads(Path(a.snapshot).read_text())
            check(
                current == expected,
                "Persistent business, directories, versions, history and exports must match the baseline exactly",
            )
    print(
        json.dumps(
            {"mode": a.mode, "checks": CHECKS, "result": "passed"}, ensure_ascii=False
        )
    )


if __name__ == "__main__":
    main()
