# RecipeFlow 接口 / API

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

同源 `/api`；GET `/auth/csrf`获取请求头与令牌，POST `/auth/login`登录。写接口需要会话和CSRF，业务修改还需`version`。账号及业务接口每次核对实时权限。

| Resource | Methods/actions |
| --- | --- |
| `/auth/me`, `/auth/logout`, `/auth/password` | GET profile, POST logout/change password |
| `/ingredients` / `/ingredients/{id}` | GET list, POST create, PUT update, DELETE only unreferenced |
| `/recipes` / `/recipes/{id}` | GET list/detail, POST create, PUT full draft with lines |
| `/recipes/{id}/preview` | GET current-quote calculation; no write |
| `/recipes/{id}/{action}` | POST submit / approve / return / revise / archive; version + note |
| `/recipes/{id}/revisions/{rev}` | GET owning recipe's historical revision |
| `/recipes/{id}/revisions/{rev}/export.csv` | GET/HEAD authorized captured ingredient CSV |
| `/batches` / `/batches/{id}` | GET list/detail, POST create, PUT draft with dishes |
| `/batches/{id}/{action}` | POST release / start / actual / complete / cancel |
| `/batches/{id}/export.csv` | GET/HEAD authorized requirement/completion CSV |
| `/options` | Kitchen-filtered accounts, ingredients, categories and current approved recipe directory |
| `/reports`, `/audit` | GET scoped statistics/audit |
| `/admin/{kind}` / `/admin/{kind}/{id}` | GET/POST/PUT fixed users, roles, departments, permissions, menus, dictionaries, settings |

Recipe line: `{ingredientId,quantity,unit}` **or** `{componentRevisionId,quantity,unit:"portion"}`. `quantity` can be a decimal string; never a browser-generated cost. Recipe fields: code/name/departmentId/reviewerId/portions/instructions/lines. Ingredient fields: code/name/departmentId/category/unit/purchaseQuantity/purchasePrice/yieldPercent/enabled.

Batch fields: code/name/departmentId/operatorId/serviceDate/dishes:`[{revisionId,portions}]`. `actual` body: version/consumption:`[{ingredientId,quantity,waste,note}]`/outputs:`[{revisionId,portions,note}]`. All requirements/outputs must be represented exactly once. `note` required for state actions (at least4characters).

Error JSON `{code}`;400 invalid formats,401 invalid authentication,403 missing permissions/scope,409 version/state/reference conflict,413 bounded-resource overflow,429 repeated login failures. No SQL, passwords or server paths are exposed.
