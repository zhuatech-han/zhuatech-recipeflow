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
  RefreshCw,
  Pencil,
  Check,
  Send,
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
  options = ref({ departments: [], users: [], ingredients: [], published: [] }),
  q = ref(""),
  state = ref(""),
  page = ref(1),
  sort = ref("code");
const detail = ref(null),
  snapshot = ref(null),
  preview = ref(null),
  history = ref(""),
  editing = ref(null),
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
    .sort((a, b) => String(a[sort.value]).localeCompare(String(b[sort.value]))),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 12, page.value * 12),
);
watch([q, state, sort], () => (page.value = 1));
/** 读取真实配方与按厨房共享的最小目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  try {
    [rows.value, options.value] = await Promise.all([
      api("/recipes"),
      api("/options"),
    ]);
  } catch (e) {
    emit("error", e);
  }
}
async function open(id) {
  try {
    detail.value = await api("/recipes/" + id);
    snapshot.value = detail.value.snapshot;
    preview.value = null;
    history.value = "";
    if (!snapshot.value && detail.value.recipe.state === "DRAFT")
      snapshot.value = await api("/recipes/" + id + "/preview");
  } catch (e) {
    emit("error", e);
  }
}
/** 当前报价只读对比，不替换批准快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function reprice() {
  try {
    preview.value = await api(
      "/recipes/" + detail.value.recipe.id + "/preview",
    );
  } catch (e) {
    emit("error", e);
  }
}
async function changeHistory() {
  try {
    if (!history.value) {
      snapshot.value = detail.value.snapshot;
      return;
    }
    snapshot.value = (
      await api(`/recipes/${detail.value.recipe.id}/revisions/${history.value}`)
    ).snapshot;
    preview.value = null;
  } catch (e) {
    emit("error", e);
  }
}
function newLine() {
  return {
    kind: "ingredient",
    ingredientId: "",
    componentRevisionId: "",
    quantity: "",
    unit: "g",
  };
}
/** 草稿编辑拷贝后提交完整明细，客户端不生成审批事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function edit() {
  await load();
  if (detail.value) {
    editing.value = {
      ...cloneRecord(detail.value.recipe),
      lines: detail.value.lines.map((x) => ({
        ...cloneRecord(x),
        kind: x.ingredientId ? "ingredient" : "component",
      })),
    };
  } else
    editing.value = {
      code: "",
      name: "",
      departmentId: options.value.departments[0]?.id || "",
      reviewerId: "",
      portions: "10",
      instructions: "",
      lines: [newLine()],
    };
}
function sourceChanged(line) {
  if (line.kind === "component") {
    line.unit = "portion";
    line.ingredientId = "";
  } else {
    line.componentRevisionId = "";
    const ingredient = options.value.ingredients.find(
      (x) => x.id === line.ingredientId,
    );
    line.unit = ["kg", "g"].includes(ingredient?.unit)
      ? "g"
      : ["l", "ml"].includes(ingredient?.unit)
        ? "ml"
        : "pc";
  }
}
const ingredients = computed(() =>
  options.value.ingredients.filter(
    (x) => x.enabled && x.departmentId === editing.value?.departmentId,
  ),
);
const components = computed(() =>
  options.value.published.filter(
    (x) =>
      x.departmentId === editing.value?.departmentId &&
      x.id !== editing.value?.id,
  ),
);
const reviewers = computed(() =>
  options.value.users.filter(
    (x) =>
      x.review &&
      x.departmentId === editing.value?.departmentId &&
      x.id !== props.me.id,
  ),
);
async function save() {
  const x = editing.value;
  const body = {
    ...x,
    lines: x.lines.map((l) => ({
      quantity: l.quantity,
      unit: l.unit,
      ...(l.kind === "ingredient"
        ? { ingredientId: l.ingredientId }
        : { componentRevisionId: l.componentRevisionId }),
    })),
  };
  const r = await props.perform(
    "/recipes" + (x.id ? "/" + x.id : ""),
    x.id ? "PUT" : "POST",
    body,
  );
  if (r) {
    editing.value = null;
    await load();
    await open(r.recipe.id);
  }
}
const actionName = (a) =>
  ({
    submit: t("提交独立审核", "Submit for review"),
    approve: t("批准并冻结", "Approve & freeze"),
    return: t("退回修改", "Return to draft"),
    revise: t("新建修订草稿", "Revise recipe"),
    archive: t("归档配方", "Archive recipe"),
  })[a] || a;
function chooseAction(a) {
  action.value = a;
  note.value = "";
}
/** 状态动作携带当前版本，审核只显示给指定账号，最终由服务端决定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function runAction() {
  const r = await props.perform(
    `/recipes/${detail.value.recipe.id}/${action.value}`,
    "POST",
    { version: detail.value.recipe.version, note: note.value },
  );
  if (r) {
    action.value = null;
    await load();
    await open(r.recipe.id);
  }
}
async function exportCsv() {
  try {
    await download(
      `/recipes/${detail.value.recipe.id}/revisions/${history.value || detail.value.recipe.activeRevisionId}/export.csv`,
      "recipe.csv",
    );
  } catch (e) {
    emit("error", e);
  }
}
const userName = (id) =>
  options.value.users.find((x) => x.id === id)?.name || "#" + id;
onMounted(load);
</script>
<template>
  <section>
    <template v-if="!detail"
      ><header class="page-heading">
        <div>
          <p class="eyebrow">RECIPE LIBRARY</p>
          <h1>{{ t("标准配方", "Standard recipes") }}</h1>
          <p class="muted">
            {{
              t(
                "配方、份数与成本，按版本管理",
                "Versioned recipes, portions and costs",
              )
            }}
          </p>
        </div>
        <div class="actions">
          <button :disabled="pending" @click="load">
            {{ t("刷新", "Refresh") }}</button
          ><button
            v-if="has('design')"
            class="primary"
            :disabled="pending"
            @click="edit"
          >
            <Plus :size="17" />{{ t("新建配方", "New recipe") }}
          </button>
        </div>
      </header>
      <div class="toolbar">
        <label class="search"
          ><Search :size="17" /><input
            v-model="q"
            :placeholder="t('配方名称或编号', 'Recipe name or code')"
            :aria-label="t('搜索配方', 'Search recipes')" /></label
        ><select v-model="state" :aria-label="t('状态筛选', 'State filter')">
          <option value="">{{ t("全部状态", "All states") }}</option>
          <option
            v-for="s in ['DRAFT', 'PENDING', 'APPROVED', 'ARCHIVED']"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option></select
        ><select v-model="sort" :aria-label="t('排序', 'Sort')">
          <option value="code">{{ t("编号排序", "By code") }}</option>
          <option value="name">{{ t("名称排序", "By name") }}</option>
        </select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("配方", "Recipe") }}</th>
              <th>{{ t("基准份数", "Recipe portions") }}</th>
              <th>{{ t("审核人", "Reviewer") }}</th>
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
              <td>{{ r.portions }}</td>
              <td>{{ userName(r.reviewerId) }}</td>
              <td>
                <span class="badge" :class="r.state">{{
                  stateName(r.state)
                }}</span>
              </td>
              <td>
                <button :disabled="pending" @click="open(r.id)">
                  {{ t("查看配方", "Open recipe") }} →
                </button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="5" class="empty">
                {{
                  t(
                    "暂无匹配配方，先维护食材报价再创建。",
                    "No matching recipes. Maintain ingredient quotes to begin.",
                  )
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="pagination">
        <span>{{ filtered.length }} {{ t("份配方", "recipes") }}</span
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
        <ArrowLeft :size="16" />{{ t("返回配方库", "Recipe library") }}
      </button>
      <header class="page-heading">
        <div>
          <p class="eyebrow">
            {{ detail.recipe.code }} · v{{ detail.recipe.version }}
          </p>
          <h1>
            {{ detail.recipe.name }}
            <span class="badge" :class="detail.recipe.state">{{
              stateName(detail.recipe.state)
            }}</span>
          </h1>
          <p class="muted">
            {{ t("基准", "Yield") }} {{ detail.recipe.portions }}
            {{ t("份 · 指定审核", "portions · Reviewer") }}
            {{ userName(detail.recipe.reviewerId) }}
          </p>
        </div>
        <div class="actions">
          <button :disabled="pending" @click="open(detail.recipe.id)">
            <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
          ><button
            v-if="detail.recipe.state === 'DRAFT' && has('design')"
            :disabled="pending"
            @click="edit"
          >
            <Pencil :size="16" />{{ t("编辑草稿", "Edit draft") }}</button
          ><button
            v-if="detail.recipe.state === 'DRAFT' && has('design')"
            class="primary"
            :disabled="pending"
            @click="chooseAction('submit')"
          >
            <Send :size="16" />{{ t("送审", "Submit") }}</button
          ><template
            v-if="
              detail.recipe.state === 'PENDING' &&
              has('review') &&
              detail.recipe.reviewerId === me.id
            "
            ><button :disabled="pending" @click="chooseAction('return')">
              {{ t("退回", "Return") }}</button
            ><button
              class="primary"
              :disabled="pending"
              @click="chooseAction('approve')"
            >
              <Check :size="16" />{{ t("批准", "Approve") }}
            </button></template
          ><button
            v-if="detail.recipe.state === 'APPROVED' && has('design')"
            :disabled="pending"
            @click="chooseAction('revise')"
          >
            {{ t("修订", "Revise") }}</button
          ><button
            v-if="
              ['DRAFT', 'APPROVED'].includes(detail.recipe.state) &&
              has('design')
            "
            :disabled="pending"
            @click="chooseAction('archive')"
          >
            {{ t("归档", "Archive") }}
          </button>
        </div>
      </header>
      <div class="panel">
        <div class="section-heading">
          <h2>{{ t("成本快照", "Cost snapshot") }}</h2>
          <div class="actions">
            <select
              v-model="history"
              :aria-label="t('历史版本', 'Revision')"
              :disabled="pending"
              @change="changeHistory"
            >
              <option value="">
                {{ t("当前版本 / 草稿", "Current / draft") }}
              </option>
              <option v-for="r in detail.revisions" :key="r.id" :value="r.id">
                R{{ r.number }} · {{ stateName(r.decision) }}
              </option></select
            ><button
              v-if="
                has('export') && (history || detail.recipe.activeRevisionId)
              "
              :disabled="pending"
              @click="exportCsv"
            >
              <Download :size="16" />CSV
            </button>
          </div>
        </div>
        <template v-if="snapshot"
          ><div class="cost-strip">
            <div>
              <small>{{ t("食材合计 · CNY", "Ingredient total · CNY") }}</small
              ><strong>¥ {{ amount(snapshot.totalCost) }}</strong>
            </div>
            <div>
              <small>{{
                t("单份食材成本 · CNY", "Ingredient cost / portion · CNY")
              }}</small
              ><strong>¥ {{ amount(snapshot.portionCost, 4) }}</strong>
            </div>
            <div>
              <small>{{ t("展开食材种数", "Expanded ingredients") }}</small
              ><strong>{{ snapshot.rows.length }}</strong>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("食材", "Ingredient") }}</th>
                  <th>{{ t("净料", "Usable quantity") }}</th>
                  <th>{{ t("毛料需求", "Raw requirement") }}</th>
                  <th>{{ t("净料率", "Yield") }}</th>
                  <th>{{ t("冻结行成本 · CNY", "Captured cost · CNY") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in snapshot.rows" :key="row.ingredientId">
                  <td>
                    <strong>{{ row.name }}</strong
                    ><small class="mono block"
                      >{{ row.code }} · {{ t("报价", "quote") }} v{{
                        row.priceVersion
                      }}</small
                    >
                  </td>
                  <td>{{ amount(row.net, 6) }} {{ row.unit }}</td>
                  <td>{{ amount(row.raw, 6) }} {{ row.unit }}</td>
                  <td>{{ amount(row.yieldPercent) }}%</td>
                  <td>¥ {{ amount(row.cost, 6) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="recipe-method">
            <h3>{{ t("制作说明", "Preparation instructions") }}</h3>
            <p>{{ snapshot.instructions }}</p>
            <p v-if="snapshot.components.length" class="hint">
              {{ t("子配方版本引用", "Pinned component versions") }}:
              <span v-for="c in snapshot.components" :key="c.revisionId"
                >#{{ c.revisionId }} × {{ c.portions }}
                {{ t("份", "portions") }} ·
              </span>
            </p>
          </div>
          <p class="hint">
            {{
              t(
                "食材成本不含人工、能源及税费。快照时间：",
                "Ingredients only; excludes labor, utilities and taxes. Captured:",
              )
            }}
            {{ time(snapshot.capturedAt) }}
          </p></template
        >
        <p v-else class="empty">
          {{
            t(
              "尚无活动成本快照，可查阅历史版本。",
              "No active cost snapshot. Historical revisions remain available.",
            )
          }}
        </p>
      </div>
      <div v-if="detail.recipe.state !== 'ARCHIVED'" class="panel">
        <div class="section-heading">
          <h2>{{ t("当前报价试算", "Current quote comparison") }}</h2>
          <button :disabled="pending" @click="reprice">
            {{ t("读取当前报价", "Compare current quotes") }}
          </button>
        </div>
        <p v-if="!preview" class="hint">
          {{
            t(
              "只读试算，不改变历史批准成本。",
              "Read-only comparison. Approved historical costs remain unchanged.",
            )
          }}
        </p>
        <div v-else class="cost-strip">
          <div>
            <small>{{ t("当前报价总成本", "Current ingredient total") }}</small
            ><strong>¥ {{ amount(preview.totalCost) }}</strong>
          </div>
          <div>
            <small>{{ t("当前单份成本", "Current cost / portion") }}</small
            ><strong>¥ {{ amount(preview.portionCost, 4) }}</strong>
          </div>
          <div v-if="snapshot">
            <small>{{
              t("与显示快照差额", "Difference from shown snapshot")
            }}</small
            ><strong
              >¥
              {{
                amount(Number(preview.totalCost) - Number(snapshot.totalCost))
              }}</strong
            >
          </div>
        </div>
      </div>
      <div class="panel">
        <h2>{{ t("配方记录", "Recipe history") }}</h2>
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
        class="extra-wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('配方草稿', 'Recipe draft')"
      >
        <header>
          <h2>
            {{
              editing.id
                ? t("编辑配方草稿", "Edit recipe draft")
                : t("新建标准配方", "New standard recipe")
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
                >{{ t("配方名称", "Recipe name")
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
                >{{ t("基准产出份数", "Recipe yield in portions")
                }}<input
                  v-model="editing.portions"
                  type="number"
                  min="1"
                  max="10000"
                  step="1"
                  required /></label
              ><label class="span2"
                >{{ t("指定独立审核人", "Independent reviewer")
                }}<select v-model="editing.reviewerId" required>
                  <option value="">
                    {{ t("请选择审核人", "Select reviewer") }}
                  </option>
                  <option v-for="u in reviewers" :key="u.id" :value="u.id">
                    {{ u.name }}
                  </option>
                </select></label
              >
            </div>
            <div class="section-heading">
              <h3>{{ t("食材与子配方", "Ingredients & components") }}</h3>
              <button
                type="button"
                :disabled="editing.lines.length >= 40"
                @click="editing.lines.push(newLine())"
              >
                <Plus :size="15" />{{ t("添加一行", "Add line") }}
              </button>
            </div>
            <div
              v-for="(line, index) in editing.lines"
              :key="index"
              class="line-form"
            >
              <label
                >{{ t("类型", "Type")
                }}<select v-model="line.kind" @change="sourceChanged(line)">
                  <option value="ingredient">
                    {{ t("直接食材", "Ingredient") }}
                  </option>
                  <option value="component">
                    {{ t("批准子配方", "Approved component") }}
                  </option>
                </select></label
              ><label class="source-select"
                >{{ t("食材 / 子配方", "Ingredient / component")
                }}<select
                  v-if="line.kind === 'ingredient'"
                  v-model="line.ingredientId"
                  required
                  @change="sourceChanged(line)"
                >
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option v-for="i in ingredients" :key="i.id" :value="i.id">
                    {{ i.code }} · {{ i.name }}
                  </option></select
                ><select v-else v-model="line.componentRevisionId" required>
                  <option value="">{{ t("请选择", "Select") }}</option>
                  <option
                    v-for="c in components"
                    :key="c.revisionId"
                    :value="c.revisionId"
                  >
                    {{ c.code }} · {{ c.name }} · #{{ c.revisionId }}
                  </option>
                </select></label
              ><label
                >{{
                  line.kind === "ingredient"
                    ? t("净料用量", "Usable quantity")
                    : t("使用份数", "Portions used")
                }}<input
                  v-model="line.quantity"
                  type="number"
                  min="0.001"
                  step="0.001"
                  max="1000000"
                  required /></label
              ><label
                >{{ t("单位", "Unit")
                }}<select v-model="line.unit">
                  <option
                    v-for="u in line.kind === 'component'
                      ? ['portion']
                      : ['g', 'kg', 'ml', 'l', 'pc']"
                    :key="u"
                    :value="u"
                  >
                    {{ u }}
                  </option>
                </select></label
              ><button
                type="button"
                :aria-label="t('删除明细', 'Remove line') + ' ' + (index + 1)"
                :disabled="editing.lines.length <= 1"
                @click="editing.lines.splice(index, 1)"
              >
                <Trash2 :size="16" />
              </button>
            </div>
            <label
              >{{ t("制作说明", "Preparation instructions")
              }}<textarea
                v-model="editing.instructions"
                required
                maxlength="2000"
                rows="4"
              ></textarea>
            </label>
            <p class="hint">
              {{
                t(
                  "直接食材填写可用净料量。子配方引用已批准版本；报价在送审时刷新，独立审核通过后冻结。",
                  "Enter usable ingredient quantities. Components reference approved revisions. Quotes refresh on submission and freeze after independent approval.",
                )
              }}
            </p>
          </fieldset>
          <footer>
            <button type="button" :disabled="pending" @click="editing = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存草稿", "Save draft") }}
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
          <p>{{ detail.recipe.name }} · v{{ detail.recipe.version }}</p>
          <p v-if="action === 'approve'" class="hint">
            {{
              t(
                "批准当前送审快照，后续备料使用该冻结版本。",
                "Approve this submitted snapshot for subsequent preparation.",
              )
            }}
          </p>
          <label
            >{{ t("操作说明", "Decision note")
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
