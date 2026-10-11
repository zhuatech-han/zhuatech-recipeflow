# RecipeFlow 操作手册 / Operations

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

## 初次配置 / Setup

登录外部配置的管理员。账号页创建配方研发、独立审核、备料计划、厨房执行等角色的账号，分配同一厨房；其他厨房单独建部门和账号。角色权限可以增减，目录管理另要求 ALL 范围。自助修改密码会退出会话。

Sign in as the externally configured administrator. Create author, reviewer, planner and kitchen-operator accounts in the same kitchen. Other kitchens use separate departments/accounts. Directory administration requires ALL scope. A password change signs the account out.

## 配方 / Recipes

维护食材报价：例如采购1kg的总价和可用净料率，而不是同时把单价和总价混写。添加食材时选正确计量维度；食材类别可以在厨房与设置中维护。配方填写基准产出份数、制作说明和每行净料量，或者已批准子配方的使用份数。保存查看成本；送审由指定独立审核人处理。报价变动时审核失败，需退回并重新送审。

Enter a purchase quantity, unit, total price and usable yield. Choose the measurement dimension carefully. A recipe contains a yield in portions, instructions and usable ingredient quantities or portions of approved component revisions. Review the cost, then submit to the assigned independent reviewer. Quote changes during review require return and resubmission.

审核人不能是任何历史编辑人；不能仅换账号再授予原编辑账号审核角色绕过规则。批准后读历史版本和CSV。当前报价试算只读，不改历史；修订后再次审核。归档不删除批准记录。

No historical editor can approve the recipe. Reassigning roles does not bypass that rule. Approved revisions and CSV remain readable. Current-quote comparison does not change history; revisions require another review. Archiving retains approved history.

## 备料 / Preparation

选择本厨房当前批准的配方版本，输入每道菜的供餐份数、日期和指定执行人。保存查看合并净料与毛料需求、食材成本。发布前可修改；若配方已进入修订，更新批准版本再发布。发布后需求冻结，可在尚未开始时取消；运行中不能取消。

Select current approved recipes from the same kitchen, requested portions, service date and operator. Saving calculates consolidated usable/raw requirements and costs. Edit before release. If a recipe changes, refresh approved references before release. Released demand is frozen; cancellation is permitted only before execution starts.

## 实际执行 / Actual execution

指定执行账号登录、开始执行。按食材基准单位逐项登记真实毛料耗用、其中损耗，并填写逐配方实际产出。初次表单实际量为空，不把计划量当实际。实际耗用包含损耗，损耗不得超过耗用。保存实际记录后另行确认完工；零耗用、损耗和产出差异需要至少4字符说明。完工后不可修改，查看冻结结果和CSV。

The assigned operator starts the plan and enters every ingredient's actual raw consumption and included waste using base units, plus actual output portions for every dish. Actual fields start empty. Waste cannot exceed consumption. Save records, then explicitly complete. Zero consumption, waste and output variances require a note of at least four characters. Completion freezes the result.

估值按计划毛料加权价格，不是实际采购支出。不存在自动库存扣减或收银同步。操作中断后刷新核对，不自动重复提交。出现版本冲突先刷新，确认最新结果再决定下一步。

Valuation uses weighted planning prices, not actual purchase spending. There are no inventory postings or POS synchronization. After interrupted submissions, refresh and check; writes are never automatically replayed.
