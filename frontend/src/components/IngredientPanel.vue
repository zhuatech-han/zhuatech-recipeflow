<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { Plus, Search, Pencil, X, Trash2 } from "@lucide/vue";
import { api } from "../api.js";
import { t, amount, cloneRecord } from "../ui.js";
const props = defineProps({
  me: { type: Object, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const rows = ref([]),
  options = ref({ departments: [], categories: [] }),
  q = ref(""),
  category = ref(""),
  enabled = ref(""),
  page = ref(1),
  sort = ref("code"),
  editing = ref(null),
  removing = ref(null);
const can = computed(() => props.me.permissions.includes("quote"));
const filtered = computed(() =>
  rows.value
    .filter(
      (r) =>
        (!q.value ||
          (r.code + " " + r.name)
            .toLowerCase()
            .includes(q.value.toLowerCase())) &&
        (!category.value || r.category === category.value) &&
        (enabled.value === "" || r.enabled === (enabled.value === "true")),
    )
    .sort((a, b) => String(a[sort.value]).localeCompare(String(b[sort.value]))),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 12, page.value * 12),
);
watch([q, category, enabled, sort], () => (page.value = 1));
/** 目录重读使用真实API，食材价格不从缓存推断。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  try {
    [rows.value, options.value] = await Promise.all([
      api("/ingredients"),
      api("/options"),
    ]);
  } catch (e) {
    emit("error", e);
  }
}
function edit(r) {
  editing.value = r
    ? cloneRecord(r)
    : {
        code: "",
        name: "",
        departmentId: options.value.departments[0]?.id || "",
        category: options.value.categories[0]?.code || "",
        unit: "kg",
        purchaseQuantity: "1",
        purchasePrice: "",
        yieldPercent: "100",
        enabled: true,
      };
}
/** 写入完成后关闭编辑并重新读目录，失败保留表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  const x = editing.value;
  const result = await props.perform(
    "/ingredients" + (x.id ? "/" + x.id : ""),
    x.id ? "PUT" : "POST",
    x,
  );
  if (result) {
    editing.value = null;
    await load();
  }
}
async function remove() {
  const x = removing.value;
  const result = await props.perform("/ingredients/" + x.id, "DELETE", {
    version: x.version,
  });
  if (result) {
    removing.value = null;
    await load();
  }
}
const departmentName = (id) =>
  options.value.departments.find((x) => x.id === id)?.name || "#" + id;
onMounted(load);
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <p class="eyebrow">INGREDIENTS</p>
        <h1>{{ t("食材与报价", "Ingredients & quotes") }}</h1>
        <p class="muted">
          {{
            t(
              "采购规格、价格与净料率",
              "Purchase quantities, prices and usable yields",
            )
          }}
        </p>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="load">
          {{ t("刷新", "Refresh") }}</button
        ><button v-if="can" class="primary" :disabled="pending" @click="edit()">
          <Plus :size="17" />{{ t("新增食材", "Add ingredient") }}
        </button>
      </div>
    </header>
    <div class="toolbar">
      <label class="search"
        ><Search :size="17" /><input
          v-model="q"
          :aria-label="t('搜索食材', 'Search ingredients')"
          :placeholder="t('名称或编号', 'Name or code')" /></label
      ><select
        v-model="category"
        :aria-label="t('分类筛选', 'Category filter')"
      >
        <option value="">{{ t("全部分类", "All categories") }}</option>
        <option v-for="c in options.categories" :key="c.id" :value="c.code">
          {{ t(c.name, c.nameEn) }}
        </option></select
      ><select v-model="enabled" :aria-label="t('启用筛选', 'Enabled filter')">
        <option value="">{{ t("全部状态", "All states") }}</option>
        <option value="true">{{ t("启用", "Enabled") }}</option>
        <option value="false">{{ t("停用", "Disabled") }}</option></select
      ><select v-model="sort" :aria-label="t('排序', 'Sort')">
        <option value="code">{{ t("编号排序", "By code") }}</option>
        <option value="name">{{ t("名称排序", "By name") }}</option>
      </select>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>{{ t("食材", "Ingredient") }}</th>
            <th>{{ t("厨房", "Kitchen") }}</th>
            <th>{{ t("采购规格", "Purchase quantity") }}</th>
            <th>{{ t("采购总价 · CNY", "Purchase total · CNY") }}</th>
            <th>{{ t("净料率", "Usable yield") }}</th>
            <th>{{ t("状态", "State") }}</th>
            <th>{{ t("操作", "Actions") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in visible" :key="r.id">
            <td>
              <strong>{{ r.name }}</strong
              ><small class="mono block">{{ r.code }} · v{{ r.version }}</small>
            </td>
            <td>{{ departmentName(r.departmentId) }}</td>
            <td>{{ amount(r.purchaseQuantity, 3) }} {{ r.unit }}</td>
            <td>¥ {{ amount(r.purchasePrice, 4) }}</td>
            <td>{{ amount(r.yieldPercent) }}%</td>
            <td>
              <span
                class="badge"
                :class="r.enabled ? 'APPROVED' : 'ARCHIVED'"
                >{{
                  r.enabled ? t("启用", "Enabled") : t("停用", "Disabled")
                }}</span
              >
            </td>
            <td>
              <div v-if="can" class="actions">
                <button
                  :disabled="pending"
                  :aria-label="t('编辑', 'Edit') + ' ' + r.name"
                  @click="edit(r)"
                >
                  <Pencil :size="16" /></button
                ><button
                  :disabled="pending"
                  :aria-label="t('删除', 'Delete') + ' ' + r.name"
                  @click="removing = r"
                >
                  <Trash2 :size="16" />
                </button>
              </div>
              <span v-else>—</span>
            </td>
          </tr>
          <tr v-if="!visible.length">
            <td colspan="7" class="empty">
              {{ t("暂无匹配食材", "No matching ingredients") }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="pagination">
      <span>{{ filtered.length }} {{ t("种食材", "ingredients") }}</span
      ><button :disabled="page <= 1" @click="page--">
        {{ t("上一页", "Previous") }}</button
      ><span
        >{{ page }} / {{ Math.max(1, Math.ceil(filtered.length / 12)) }}</span
      ><button :disabled="page * 12 >= filtered.length" @click="page++">
        {{ t("下一页", "Next") }}
      </button>
    </footer>
    <p class="hint">
      {{
        t(
          "报价更新不会修改已批准配方和已发布计划的历史成本。",
          "Quote updates do not rewrite approved recipes or released plans.",
        )
      }}
    </p>
    <div
      v-if="editing"
      class="modal"
      @click.self="!pending && (editing = null)"
    >
      <section
        class="wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('食材报价', 'Ingredient quote')"
      >
        <header>
          <h2>
            {{
              editing.id
                ? t("编辑食材报价", "Edit quote")
                : t("新增食材", "Add ingredient")
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
                >{{ t("名称", "Name")
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
                >{{ t("分类", "Category")
                }}<select v-model="editing.category" required>
                  <option
                    v-for="c in options.categories"
                    :key="c.id"
                    :value="c.code"
                  >
                    {{ t(c.name, c.nameEn) }}
                  </option>
                </select></label
              ><label
                >{{ t("采购数量", "Purchase quantity")
                }}<input
                  v-model="editing.purchaseQuantity"
                  type="number"
                  step="0.001"
                  min="0.001"
                  max="1000000"
                  required /></label
              ><label
                >{{ t("采购单位", "Purchase unit")
                }}<select v-model="editing.unit">
                  <option
                    v-for="u in ['kg', 'g', 'l', 'ml', 'pc']"
                    :key="u"
                    :value="u"
                  >
                    {{ u }}
                  </option>
                </select></label
              ><label
                >{{
                  t(
                    "上述采购数量总价 · CNY",
                    "Total price for this quantity · CNY",
                  )
                }}<input
                  v-model="editing.purchasePrice"
                  type="number"
                  step="0.0001"
                  min="0"
                  max="1000000"
                  required /></label
              ><label
                >{{ t("净料率 %", "Usable yield %")
                }}<input
                  v-model="editing.yieldPercent"
                  type="number"
                  step="0.01"
                  min="0.01"
                  max="100"
                  required
              /></label>
            </div>
            <label class="check"
              ><input v-model="editing.enabled" type="checkbox" />{{
                t("启用", "Enabled")
              }}</label
            >
            <p class="hint">
              {{
                t(
                  "kg/g 为重量，l/ml 为体积，pc 为计数。已有食材不能改变计量维度。",
                  "kg/g: mass; l/ml: volume; pc: count. Existing measurement dimensions are immutable.",
                )
              }}
            </p>
          </fieldset>
          <footer>
            <button type="button" :disabled="pending" @click="editing = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存报价", "Save quote") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
    <div
      v-if="removing"
      class="modal"
      @click.self="!pending && (removing = null)"
    >
      <section
        role="dialog"
        aria-modal="true"
        :aria-label="t('删除食材', 'Delete ingredient')"
      >
        <h2>{{ t("删除食材", "Delete ingredient") }} · {{ removing.name }}</h2>
        <p>
          {{
            t(
              "只允许删除尚未被配方和历史引用的食材；已有引用请停用。",
              "Only unreferenced ingredients can be deleted. Disable referenced ingredients instead.",
            )
          }}
        </p>
        <footer>
          <button :disabled="pending" @click="removing = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="danger" :disabled="pending" @click="remove">
            {{ t("确认删除", "Delete") }}
          </button>
        </footer>
      </section>
    </div>
  </section>
</template>
