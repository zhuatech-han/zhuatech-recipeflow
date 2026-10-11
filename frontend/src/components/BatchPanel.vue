<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import {
  Plus,
  Search,
  ArrowLeft,
  X,
  Trash2,
  Download,
  Pencil,
  RefreshCw,
  Play,
  Check,
} from "@lucide/vue";
import { api, download } from "../api.js";
import { t, amount, stateName, time, cloneRecord } from "../ui.js";
const props = defineProps({
  me: { type: Object, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const rows = ref([]),
  options = ref({ users: [], departments: [], published: [] }),
  q = ref(""),
  state = ref(""),
  page = ref(1),
  sort = ref("serviceDate");
const detail = ref(null),
  editing = ref(null),
  execution = ref(null),
  action = ref(null),
  note = ref("");
const has = (p) => props.me.permissions.includes(p);
const filtered = computed(() =>
  rows.value
    .filter(
      (x) =>
        (!state.value || x.state === state.value) &&
        (!q.value ||
          (x.code + " " + x.name)
            .toLowerCase()
            .includes(q.value.toLowerCase())),
    )
    .sort((a, b) =>
      sort.value === "serviceDate"
        ? b.serviceDate.localeCompare(a.serviceDate)
        : a.code.localeCompare(b.code),
    ),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 12, page.value * 12),
);
watch([q, state, sort], () => (page.value = 1));
/** 实际API返回授权批次，数量与成本来自服务端快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  try {
    [rows.value, options.value] = await Promise.all([
      api("/batches"),
      api("/options"),
    ]);
  } catch (e) {
    emit("error", e);
  }
}
async function open(id) {
  try {
    detail.value = await api("/batches/" + id);
  } catch (e) {
    emit("error", e);
  }
}
async function edit() {
  await load();
  editing.value = detail.value
    ? {
        ...cloneRecord(detail.value.batch),
        dishes: detail.value.preparation.dishes.map((x) => ({
          revisionId: x.revisionId,
          portions: String(x.portions),
        })),
      }
    : {
        code: "",
        name: "",
        departmentId: options.value.departments[0]?.id || "",
        operatorId: "",
        serviceDate: new Date().toLocaleDateString("en-CA"),
        dishes: [{ revisionId: "", portions: "" }],
      };
}
const operators = computed(() =>
  options.value.users.filter(
    (u) => u.execute && u.departmentId === editing.value?.departmentId,
  ),
);
const published = computed(() =>
  options.value.published.filter(
    (x) => x.departmentId === editing.value?.departmentId,
  ),
);
async function save() {
  const x = editing.value;
  const r = await props.perform(
    "/batches" + (x.id ? "/" + x.id : ""),
    x.id ? "PUT" : "POST",
    x,
  );
  if (r) {
    editing.value = null;
    await load();
    await open(r.batch.id);
  }
}
const actionName = (a) =>
  ({
    release: t("发布备料计划", "Release preparation"),
    start: t("开始厨房执行", "Start preparation"),
    complete: t("确认完工", "Complete preparation"),
    cancel: t("取消计划", "Cancel preparation"),
  })[a] || a;
function chooseAction(a) {
  action.value = a;
  note.value = "";
}
/** 发布和完工操作显式确认，网络中断不自动重放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function runAction() {
  const r = await props.perform(
    `/batches/${detail.value.batch.id}/${action.value}`,
    "POST",
    { version: detail.value.batch.version, note: note.value },
  );
  if (r) {
    action.value = null;
    await load();
    await open(r.batch.id);
  }
}
/** 实际数量初次为空，必须由操作者录入，不能用计划量冒充实际。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function editActual() {
  execution.value = {
    consumption: detail.value.preparation.requirements.map((req) => {
      const saved = detail.value.consumption.find(
        (x) => x.ingredientId === req.ingredientId,
      );
      return {
        ingredientId: req.ingredientId,
        quantity: saved ? String(saved.quantity) : "",
        waste: saved ? String(saved.waste) : "0",
        note: saved?.note || "",
      };
    }),
    outputs: detail.value.preparation.dishes.map((d) => {
      const saved = detail.value.outputs.find(
        (x) => x.revisionId === d.revisionId,
      );
      return {
        revisionId: d.revisionId,
        portions: saved ? String(saved.portions) : "",
        note: saved?.note || "",
      };
    }),
  };
}
async function saveActual() {
  const r = await props.perform(
    `/batches/${detail.value.batch.id}/actual`,
    "POST",
    { version: detail.value.batch.version, ...execution.value },
  );
  if (r) {
    execution.value = null;
    await open(r.batch.id);
  }
}
async function exportCsv() {
  try {
    await download(
      `/batches/${detail.value.batch.id}/export.csv`,
      "preparation.csv",
    );
  } catch (e) {
    emit("error", e);
  }
}
const userName = (id) =>
  options.value.users.find((x) => x.id === id)?.name || "#" + id;
const requirement = (id) =>
  detail.value.preparation.requirements.find((x) => x.ingredientId === id);
const dish = (id) =>
  detail.value.preparation.dishes.find((x) => x.revisionId === id);
onMounted(load);
</script>
<template>
  <section>
    <template v-if="!detail"
      ><header class="page-heading">
        <div>
          <p class="eyebrow">KITCHEN PREPARATION</p>
          <h1>{{ t("备料计划", "Preparation plans") }}</h1>
          <p class="muted">
            {{
              t(
                "批准配方 → 供餐份数 → 食材需求 → 实际执行",
                "Approved recipes → Portions → Requirements → Actual execution",
              )
            }}
          </p>
        </div>
        <div class="actions">
          <button :disabled="pending" @click="load">
            {{ t("刷新", "Refresh") }}</button
          ><button
            v-if="has('plan')"
            class="primary"
            :disabled="pending"
            @click="edit"
          >
            <Plus :size="17" />{{ t("新建备料计划", "New preparation") }}
          </button>
        </div>
      </header>
      <div class="toolbar">
        <label class="search"
          ><Search :size="17" /><input
            v-model="q"
            :placeholder="t('计划名称或编号', 'Plan name or code')"
            :aria-label="t('搜索备料', 'Search preparation')" /></label
        ><select v-model="state" :aria-label="t('状态筛选', 'State filter')">
          <option value="">{{ t("全部状态", "All states") }}</option>
          <option
            v-for="s in [
              'DRAFT',
              'RELEASED',
              'RUNNING',
              'COMPLETED',
              'CANCELLED',
            ]"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option></select
        ><select v-model="sort" :aria-label="t('排序', 'Sort')">
          <option value="serviceDate">
            {{ t("供餐日期倒序", "Latest service date") }}
          </option>
          <option value="code">{{ t("编号排序", "By code") }}</option>
        </select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("计划", "Plan") }}</th>
              <th>{{ t("供餐日期", "Service date") }}</th>
              <th>{{ t("执行人", "Operator") }}</th>
              <th>{{ t("状态", "State") }}</th>
              <th>{{ t("操作", "Actions") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in visible" :key="r.id">
              <td>
                <strong>{{ r.name }}</strong
                ><small class="block mono">{{ r.code }}</small>
              </td>
              <td>{{ r.serviceDate }}</td>
              <td>{{ userName(r.operatorId) }}</td>
              <td>
                <span class="badge" :class="r.state">{{
                  stateName(r.state)
                }}</span>
              </td>
              <td>
                <button :disabled="pending" @click="open(r.id)">
                  {{ t("查看计划", "Open plan") }} →
                </button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="5" class="empty">
                {{
                  t(
                    "暂无匹配计划，请先批准配方。",
                    "No matching plans. Approve recipes to begin.",
                  )
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>{{ filtered.length }} {{ t("个批次", "batches") }}</span
        ><button :disabled="page <= 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><span
          >{{ page }} / {{ Math.max(1, Math.ceil(filtered.length / 12)) }}</span
        ><button :disabled="page * 12 >= filtered.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer></template
    >
    <template v-else
      ><button class="back" :disabled="pending" @click="detail = null">
        <ArrowLeft :size="16" />{{ t("返回备料计划", "Preparation plans") }}
      </button>
      <header class="page-heading">
        <div>
          <p class="eyebrow">
            {{ detail.batch.code }} · v{{ detail.batch.version }}
          </p>
          <h1>
            {{ detail.batch.name }}
            <span class="badge" :class="detail.batch.state">{{
              stateName(detail.batch.state)
            }}</span>
          </h1>
          <p class="muted">
            {{ detail.batch.serviceDate }} · {{ t("指定执行", "Operator") }}
            {{ userName(detail.batch.operatorId) }}
          </p>
        </div>
        <div class="actions">
          <button :disabled="pending" @click="open(detail.batch.id)">
            <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
          ><button v-if="has('export')" :disabled="pending" @click="exportCsv">
            <Download :size="16" />CSV</button
          ><template v-if="has('plan') && detail.batch.state === 'DRAFT'"
            ><button :disabled="pending" @click="edit">
              <Pencil :size="16" />{{ t("编辑需求", "Edit demand") }}</button
            ><button
              class="primary"
              :disabled="pending"
              @click="chooseAction('release')"
            >
              {{ t("发布计划", "Release") }}
            </button></template
          ><button
            v-if="
              has('plan') && ['DRAFT', 'RELEASED'].includes(detail.batch.state)
            "
            :disabled="pending"
            @click="chooseAction('cancel')"
          >
            {{ t("取消计划", "Cancel") }}</button
          ><button
            v-if="
              has('execute') &&
              detail.batch.operatorId === me.id &&
              detail.batch.state === 'RELEASED'
            "
            class="primary"
            :disabled="pending"
            @click="chooseAction('start')"
          >
            <Play :size="16" />{{ t("开始执行", "Start") }}</button
          ><template
            v-if="
              has('execute') &&
              detail.batch.operatorId === me.id &&
              detail.batch.state === 'RUNNING'
            "
            ><button :disabled="pending" @click="editActual">
              {{ t("登记耗用与产出", "Enter actuals") }}</button
            ><button
              class="primary"
              :disabled="pending"
              @click="chooseAction('complete')"
            >
              <Check :size="16" />{{ t("确认完工", "Complete") }}
            </button></template
          >
        </div>
      </header>
      <div class="metrics">
        <article>
          <small>{{ t("供餐份数", "Requested portions") }}</small
          ><strong>{{
            detail.preparation.dishes.reduce((n, d) => n + d.portions, 0)
          }}</strong>
        </article>
        <article>
          <small>{{ t("菜品 / 食材", "Dishes / ingredients") }}</small
          ><strong
            >{{ detail.preparation.dishes.length
            }}<em>/ {{ detail.preparation.requirements.length }}</em></strong
          >
        </article>
        <article>
          <small>{{
            t("计划食材成本 · CNY", "Planned ingredient cost · CNY")
          }}</small
          ><strong>¥ {{ amount(detail.preparation.totalCost) }}</strong>
        </article>
      </div>
      <div class="panel">
        <h2>{{ t("菜品与制作说明", "Dishes & instructions") }}</h2>
        <details
          v-for="d in detail.preparation.dishes"
          :key="d.revisionId"
          class="dish"
        >
          <summary>
            <strong>{{ d.recipe.name }}</strong
            ><span
              >{{ d.portions }} {{ t("份", "portions") }} · #{{
                d.revisionId
              }}</span
            >
          </summary>
          <p class="recipe-method">{{ d.recipe.instructions }}</p>
          <div
            v-for="(c, index) in d.componentTasks"
            :key="index"
            class="component-task"
          >
            <strong
              >{{ t("子配方制作", "Component preparation") }} ·
              {{ c.name }}</strong
            ><small class="block"
              >{{ amount(c.portions, 6) }} {{ t("份", "portions") }} · #{{
                c.revisionId
              }}</small
            >
            <p class="recipe-method">{{ c.instructions }}</p>
          </div>
        </details>
      </div>
      <div class="panel">
        <h2>{{ t("汇总备料清单", "Consolidated requirements") }}</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("食材", "Ingredient") }}</th>
                <th>{{ t("净料量", "Usable quantity") }}</th>
                <th>{{ t("毛料需求", "Raw requirement") }}</th>
                <th>{{ t("计划成本 · CNY", "Planned cost · CNY") }}</th>
                <th>{{ t("已录耗用", "Entered consumption") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in detail.preparation.requirements"
                :key="r.ingredientId"
              >
                <td>
                  <strong>{{ r.name }}</strong
                  ><small class="block mono">{{ r.code }}</small>
                </td>
                <td>{{ amount(r.net, 6) }} {{ r.unit }}</td>
                <td>{{ amount(r.raw, 6) }} {{ r.unit }}</td>
                <td>¥ {{ amount(r.cost, 6) }}</td>
                <td>
                  {{
                    detail.consumption.find(
                      (x) => x.ingredientId === r.ingredientId,
                    )
                      ? amount(
                          detail.consumption.find(
                            (x) => x.ingredientId === r.ingredientId,
                          ).quantity,
                          3,
                        ) +
                        " " +
                        r.unit
                      : "—"
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="hint">
          {{
            t(
              "需求来自批准快照。毛料含净料率折算，同一食材按各版本冻结价格贡献汇总。",
              "Requirements use approved snapshots and captured usable yields. Ingredient costs preserve each recipe’s captured prices.",
            )
          }}
        </p>
      </div>
      <div v-if="detail.result" class="panel">
        <div class="section-heading">
          <h2>{{ t("完工与差异", "Completion & variances") }}</h2>
          <span class="badge COMPLETED">{{
            time(detail.result.completedAt)
          }}</span>
        </div>
        <div class="cost-strip">
          <div>
            <small>{{ t("实际耗用估值", "Estimated actual cost") }}</small
            ><strong>¥ {{ amount(detail.result.estimatedActualCost) }}</strong>
          </div>
          <div>
            <small>{{ t("成本差异", "Cost variance") }}</small
            ><strong>¥ {{ amount(detail.result.costVariance) }}</strong>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("食材", "Ingredient") }}</th>
                <th>{{ t("实际耗用", "Actual raw use") }}</th>
                <th>{{ t("其中损耗", "Waste included") }}</th>
                <th>{{ t("毛料差异", "Raw variance") }}</th>
                <th>{{ t("估值 · CNY", "Estimated cost · CNY") }}</th>
                <th>{{ t("说明", "Note") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in detail.result.consumption" :key="r.ingredientId">
                <td>{{ requirement(r.ingredientId).name }}</td>
                <td>
                  {{ amount(r.quantity, 3) }}
                  {{ requirement(r.ingredientId).unit }}
                </td>
                <td>{{ amount(r.waste, 3) }}</td>
                <td>{{ amount(r.quantityVariance, 6) }}</td>
                <td>¥ {{ amount(r.estimatedCost) }}</td>
                <td>{{ r.note || "—" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <h3>{{ t("实际产出", "Actual portions") }}</h3>
        <p v-for="o in detail.result.outputs" :key="o.revisionId">
          {{ dish(o.revisionId).recipe.name }} · {{ o.portions }} /
          {{ dish(o.revisionId).portions }} {{ t("份", "portions") }} ·
          {{ o.note || "—" }}
        </p>
        <p class="hint">
          {{
            t(
              "耗用包含损耗，损耗不重复计价。按计划加权单价估值，不是实际采购成本或完整利润。",
              "Consumption includes waste. No double counting. Values use weighted planning prices, not actual purchase costs or total profit.",
            )
          }}
        </p>
      </div>
      <div class="panel">
        <h2>{{ t("执行记录", "Execution history") }}</h2>
        <ol class="timeline">
          <li v-for="e in detail.events.slice(-10).reverse()" :key="e.id">
            <span class="badge">{{ e.action }}</span
            ><strong>{{ userName(e.actorId) }}</strong
            ><small>{{ time(e.createdAt) }}</small>
            <p v-if="e.note">{{ e.note }}</p>
          </li>
        </ol>
      </div></template
    >
    <div
      v-if="editing"
      class="modal"
      @click.self="!pending && (editing = null)"
    >
      <section
        class="wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('备料需求', 'Preparation demand')"
      >
        <header>
          <h2>
            {{
              editing.id
                ? t("编辑备料需求", "Edit preparation")
                : t("新建备料计划", "New preparation")
            }}
          </h2>
          <button :disabled="pending" @click="editing = null">
            <X :size="18" />
          </button>
        </header>
        <form @submit.prevent="save">
          <fieldset :disabled="pending">
            <div class="form-grid">
              <label
                >{{ t("编号", "Code")
                }}<input
                  v-model="editing.code"
                  required
                  pattern="[A-Z0-9_]{2,30}"
                  maxlength="30"
                  :readonly="!!editing.id" /></label
              ><label
                >{{ t("计划名称", "Plan name")
                }}<input
                  v-model="editing.name"
                  required
                  maxlength="120" /></label
              ><label
                >{{ t("厨房", "Kitchen")
                }}<select
                  v-model="editing.departmentId"
                  required
                  :disabled="!!editing.id"
                >
                  <option
                    v-for="d in options.departments"
                    :key="d.id"
                    :value="d.id"
                  >
                    {{ d.name }}
                  </option>
                </select></label
              ><label
                >{{ t("供餐日期", "Service date")
                }}<input
                  v-model="editing.serviceDate"
                  type="date"
                  required
                  min="2000-01-01"
                  max="2100-12-31" /></label
              ><label class="span2"
                >{{ t("指定厨房执行人", "Assigned kitchen operator")
                }}<select v-model="editing.operatorId" required>
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option v-for="u in operators" :key="u.id" :value="u.id">
                    {{ u.name }}
                  </option>
                </select></label
              >
            </div>
            <div class="section-heading">
              <h3>{{ t("批准配方与份数", "Approved recipes & portions") }}</h3>
              <button
                type="button"
                :disabled="editing.dishes.length >= 20"
                @click="editing.dishes.push({ revisionId: '', portions: '' })"
              >
                <Plus :size="15" />{{ t("添加菜品", "Add dish") }}
              </button>
            </div>
            <div v-for="(d, i) in editing.dishes" :key="i" class="dish-form">
              <label
                >{{ t("批准配方", "Approved recipe")
                }}<select v-model="d.revisionId" required>
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option
                    v-for="r in published"
                    :key="r.revisionId"
                    :value="r.revisionId"
                  >
                    {{ r.code }} · {{ r.name }} · #{{ r.revisionId }}
                  </option>
                </select></label
              ><label
                >{{ t("供餐份数", "Requested portions")
                }}<input
                  v-model="d.portions"
                  type="number"
                  min="1"
                  max="100000"
                  step="1"
                  required /></label
              ><button
                type="button"
                :disabled="editing.dishes.length <= 1"
                :aria-label="t('删除菜品', 'Remove dish') + ' ' + (i + 1)"
                @click="editing.dishes.splice(i, 1)"
              >
                <Trash2 :size="16" />
              </button>
            </div>
            <p v-if="!published.length" class="hint">
              {{
                t(
                  "当前厨房没有已批准配方。",
                  "No approved recipes in this kitchen.",
                )
              }}
            </p>
            <p class="hint">
              {{
                t(
                  "保存后生成食材需求，发布后冻结。实际耗用需执行人另行录入。",
                  "Saving calculates requirements. Releasing freezes them. The operator enters actual use separately.",
                )
              }}
            </p>
          </fieldset>
          <footer>
            <button type="button" :disabled="pending" @click="editing = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存需求", "Save demand") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
    <div
      v-if="execution"
      class="modal"
      @click.self="!pending && (execution = null)"
    >
      <section
        class="extra-wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('登记实际耗用', 'Enter actual use')"
      >
        <header>
          <h2>{{ t("登记耗用与产出", "Enter consumption & output") }}</h2>
          <button :disabled="pending" @click="execution = null">
            <X :size="18" />
          </button>
        </header>
        <form @submit.prevent="saveActual">
          <fieldset :disabled="pending">
            <p class="hint">
              {{
                t(
                  "实际毛料耗用包含其中损耗；不要重复填入损耗。所有数量均为食材基准单位。",
                  "Raw consumption includes waste. Do not count waste twice. All quantities use ingredient base units.",
                )
              }}
            </p>
            <div
              v-for="r in execution.consumption"
              :key="r.ingredientId"
              class="actual-row"
            >
              <div class="section-heading">
                <strong
                  >{{ requirement(r.ingredientId).name }} ·
                  {{ requirement(r.ingredientId).unit }}</strong
                ><small
                  >{{ t("计划毛料", "Planned raw") }}
                  {{ amount(requirement(r.ingredientId).raw, 6) }}</small
                >
              </div>
              <div class="form-grid">
                <label
                  >{{ t("实际毛料耗用", "Actual raw consumption")
                  }}<input
                    v-model="r.quantity"
                    type="number"
                    min="0"
                    max="1000000000"
                    step="0.001"
                    required /></label
                ><label
                  >{{ t("其中损耗", "Waste included")
                  }}<input
                    v-model="r.waste"
                    type="number"
                    min="0"
                    :max="r.quantity || 0"
                    step="0.001"
                    required /></label
                ><label class="span2"
                  >{{
                    t(
                      "耗用说明（零耗用或损耗须填写）",
                      "Consumption note (required for zero use or waste)",
                    )
                  }}<input
                    v-model="r.note"
                    maxlength="400"
                    :required="Number(r.quantity) === 0 || Number(r.waste) > 0"
                    :minlength="
                      Number(r.quantity) === 0 || Number(r.waste) > 0 ? 4 : 0
                    "
                /></label>
              </div>
            </div>
            <h3>{{ t("实际产出份数", "Actual output portions") }}</h3>
            <div
              v-for="o in execution.outputs"
              :key="o.revisionId"
              class="output-row"
            >
              <strong
                >{{ dish(o.revisionId).recipe.name }} ·
                {{ t("计划", "Planned") }}
                {{ dish(o.revisionId).portions }}</strong
              >
              <div class="form-grid">
                <label
                  >{{ t("实际产出", "Actual output")
                  }}<input
                    v-model="o.portions"
                    type="number"
                    min="0"
                    max="100000"
                    step="1"
                    required /></label
                ><label
                  >{{
                    t(
                      "产出说明（差异须填写）",
                      "Output note (required for variance)",
                    )
                  }}<input
                    v-model="o.note"
                    maxlength="400"
                    :required="
                      Number(o.portions) !== dish(o.revisionId).portions
                    "
                    :minlength="
                      Number(o.portions) !== dish(o.revisionId).portions ? 4 : 0
                    "
                /></label>
              </div>
            </div>
          </fieldset>
          <footer>
            <button type="button" :disabled="pending" @click="execution = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存实际记录", "Save actual records") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
    <div v-if="action" class="modal" @click.self="!pending && (action = null)">
      <section role="dialog" aria-modal="true" :aria-label="actionName(action)">
        <header>
          <h2>{{ actionName(action) }}</h2>
          <button :disabled="pending" @click="action = null">
            <X :size="18" />
          </button>
        </header>
        <form @submit.prevent="runAction">
          <p>{{ detail.batch.name }} · v{{ detail.batch.version }}</p>
          <p v-if="action === 'complete'" class="hint">
            {{
              t(
                "确认已录入全部实际耗用和产出。完工后记录冻结。",
                "Verify all consumption and output records. Completion freezes the result.",
              )
            }}
          </p>
          <label
            >{{ t("操作说明", "Action note")
            }}<textarea
              v-model="note"
              required
              minlength="4"
              maxlength="400"
              rows="3"
              :disabled="pending"
            ></textarea>
          </label>
          <footer>
            <button type="button" :disabled="pending" @click="action = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ actionName(action) }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
