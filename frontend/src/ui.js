// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 切换界面语言，不翻译或改写业务输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
const states = {
  DRAFT: ["草稿", "Draft"],
  PENDING: ["待独立审核", "In review"],
  APPROVED: ["已批准", "Approved"],
  ARCHIVED: ["已归档", "Archived"],
  RELEASED: ["已发布", "Released"],
  RUNNING: ["执行中", "Running"],
  COMPLETED: ["已完成", "Completed"],
  CANCELLED: ["已取消", "Cancelled"],
  RETURNED: ["已退回", "Returned"],
};
/** 状态名称只映射服务端实际状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const stateName = (s) => (states[s] ? t(...states[s]) : s);
const errors = {
  UNAUTHENTICATED: [
    "会话失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号、密码错误或账号已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有这项操作权限。", "Permission denied."],
  OUT_OF_SCOPE: [
    "记录不在当前厨房数据范围。",
    "Record outside your kitchen scope.",
  ],
  VERSION_CONFLICT: [
    "记录已变化，请刷新核对后再提交。",
    "Record changed. Refresh before submitting.",
  ],
  STATE_INVALID: [
    "当前状态不能执行此操作。",
    "Action unavailable in this state.",
  ],
  INDEPENDENT_REQUIRED: [
    "指定审核人必须不是任何历史编辑人。",
    "The reviewer must be independent of every historical editor.",
  ],
  REVIEWER_ONLY: [
    "仅指定审核人可以批准或退回。",
    "Only the assigned reviewer may decide.",
  ],
  REVIEWER_UNAVAILABLE: [
    "审核人账号、厨房或权限不匹配。",
    "Reviewer unavailable or incompatible.",
  ],
  OPERATOR_ONLY: [
    "仅指定厨房执行人可以办理。",
    "Only the assigned operator may execute.",
  ],
  OPERATOR_UNAVAILABLE: [
    "执行人账号、厨房或权限不匹配。",
    "Operator unavailable or incompatible.",
  ],
  PRICE_CHANGED: [
    "送审后报价变化，请退回重新送审。",
    "Ingredient quote changed. Return and resubmit.",
  ],
  SNAPSHOT_STALE: [
    "批准配方已修订，请更新备料草稿。",
    "Approved recipe changed. Update the draft.",
  ],
  RECIPE_UNAVAILABLE: [
    "配方必须是当前批准版本。",
    "A current approved recipe is required.",
  ],
  COMPONENT_UNAVAILABLE: [
    "子配方版本未批准或厨房不匹配。",
    "Component not approved or in another kitchen.",
  ],
  RECIPE_CYCLE: [
    "子配方存在循环依赖，未保存。",
    "Recipe dependency cycle. Not saved.",
  ],
  COMPONENT_LIMIT: [
    "子配方制作步骤最多200项。",
    "Up to 200 component preparation steps.",
  ],
  RECIPE_DEPTH: [
    "子配方最多5层、展开100种食材。",
    "Up to five levels and 100 ingredients.",
  ],
  INGREDIENT_UNAVAILABLE: [
    "食材停用或厨房不匹配。",
    "Ingredient disabled or in another kitchen.",
  ],
  UNIT_MISMATCH: [
    "重量、体积、计数不能跨维度换算。",
    "Mass, volume and count cannot be mixed.",
  ],
  UNIT_INVALID: ["计量单位不正确。", "Invalid unit."],
  INVALID_DECIMAL: [
    "数值超精度或范围不正确。",
    "Invalid decimal precision or range.",
  ],
  INVALID_INPUT: ["字段格式不正确。", "Invalid fields."],
  LINE_INVALID: [
    "每行只能引用食材或子配方之一。",
    "Each line requires one ingredient or component.",
  ],
  LINE_LIMIT: [
    "配方1–40行；备料1–20种菜品。",
    "Recipes: 1–40 lines. Preparation: 1–20 dishes.",
  ],
  ACTUAL_INCOMPLETE: [
    "请完整填写每种食材耗用和每道菜产出。",
    "Enter every consumption and output row.",
  ],
  ACTUAL_INVALID: [
    "实际行重复或不属于当前需求。",
    "Duplicate or unknown actual row.",
  ],
  NOTE_REQUIRED: [
    "请填写至少4个字符的说明。",
    "Enter a note of at least four characters.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72 UTF-8字节，含大小写及数字。",
    "Use 12–72 UTF-8 bytes, uppercase, lowercase and digits.",
  ],
  LAST_ADMIN: [
    "必须保留一个完整管理员。",
    "Keep at least one full administrator.",
  ],
  ACCOUNT_ASSIGNED: [
    "已有业务历史，不能更换账号厨房。",
    "Business references prevent kitchen reassignment.",
  ],
  IDENTITY_LOCKED: [
    "编号、厨房或计量维度不可修改。",
    "Code, kitchen or measurement dimension is immutable.",
  ],
  CATEGORY_INVALID: [
    "食材分类不存在或已停用。",
    "Category missing or disabled.",
  ],
  REFERENCED: [
    "已被配方引用，请使用停用。",
    "Ingredient is referenced. Disable it instead.",
  ],
  CONFLICT: ["编号重复或仍被引用。", "Duplicate code or referenced record."],
  RESULT_UNKNOWN: [
    "提交结果未知，请刷新核对后再操作。",
    "Submission result unknown. Refresh and check.",
  ],
  NETWORK_ERROR: [
    "连接失败，请刷新重试。",
    "Connection failed. Refresh and retry.",
  ],
  OLD_PASSWORD_INVALID: ["原密码错误。", "Current password is incorrect."],
  RESOURCE_LIMIT: ["记录达到系统上限。", "Resource limit reached."],
  REVISION_LIMIT: ["已达到历史版本上限。", "Revision limit reached."],
  QUANTITY_LIMIT: [
    "用量超过支持范围。",
    "Quantity exceeds the supported range.",
  ],
  QUANTITY_TOO_SMALL: [
    "展开用量低于最小有效量。",
    "Expanded quantity is too small.",
  ],
  SNAPSHOT_CHANGED: [
    "快照校验不一致，操作已拒绝。",
    "Snapshot digest mismatch. Action rejected.",
  ],
  REVISION_MISMATCH: [
    "版本不属于当前配方。",
    "Revision belongs to another recipe.",
  ],
  CODE_INVALID: [
    "编号使用2–30位大写字母、数字或下划线。",
    "Use 2–30 uppercase letters, digits or underscores.",
  ],
  DUPLICATE_DISH: [
    "同一配方版本只能添加一次。",
    "Add each recipe version only once.",
  ],
};
/** 未知错误明确呈现，不按成功处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成：", "Action failed: ") + e.message;
/** 拷贝列表记录，避免表单修改显示的原数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
/** 仅格式化显示；服务端使用十进制计算，浏览器不计算成本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const amount = (n, digits = 2) =>
  n == null
    ? "—"
    : Number(n).toLocaleString(language.value === "zh" ? "zh-CN" : "en-US", {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
      });
/** 日期按当前浏览器时区显示，业务供餐日期为无时区日历日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const time = (s) => (s ? new Date(s).toLocaleString() : "");
