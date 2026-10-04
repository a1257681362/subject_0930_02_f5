# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

## 海水养殖台账（aqua-1）

模块包路径 `com.evops.aqua`，已打通：建池 → 投苗 → 日常记录 → 饲料/用药 → 抽检 → 换池 → 出池。成本核算留待后续版本。

- 养殖池档案：水深、盐度、所属海区（`POST /api/aqua/ponds`）
- 投苗批次：苗种来源、入池数量、计划出池日；批次编号全局唯一（`POST /api/aqua/batches`）
- 饲料、用药、日常、抽检：按池按批留痕（`/api/aqua/records/**`），批次不在该池或已出池会被拒绝
- 换池：`POST /api/aqua/batches/{id}/transfer` 自动生成转移记录并更新批次所在池
- 出池：`POST /api/aqua/batches/{id}/harvest` 生成出池记录，批次封存
- 原始投苗单不可删除：无 DELETE 接口，表上无删除标记
- 批次全链路追溯：`GET /api/aqua/batches/{id}/trace`

接口需先登录：`POST /api/auth/login`（bootstrap / bootstrap）。

验收：`mvn test -Dtest=AquaLedgerAcceptanceTest`，从养殖池档案入口追到投苗批次落库，逐检查点输出报告，失败即停并给出停点。

## 水质采样计划与缺测补录（aqua-2）

模块包路径 `com.evops.aqua.wq`，在台账之上打通：分阶段规则版本 → 采样计划 → 按池生成任务 → 草稿/提交 → 缺测补录 → 主管审核 → 池塘健康曲线，全程留证。

- 指标规则版本化（`POST /api/aqua/wq/rule-versions`）：不同养殖阶段配置采样频率（间隔天数）与指标正常范围；新版本发布后旧版本自动停用，**新版本只作用于未开始任务**——在执行计划的版本指针切换，从未录入过的草稿任务按新版本重建指标快照；已暂存/已提交/已审核任务冻结旧版本不变。
- 采样计划（`/api/aqua/wq/plans`）：绑定规则版本与养殖阶段、覆盖日期区间与池塘；`POST /{id}/tasks/generate` **按池按频率生成任务**，同计划同池同日幂等，重复请求不产生第二条结果。
- 采样执行（`/api/aqua/wq/tasks/{id}`）：`PUT /draft` 可只录部分指标暂存草稿；`POST /submit` 提交时每个指标必须有值，**缺测项必须说明原因**，否则拒绝；重复提交返回同一任务。
- 缺测/历史补录（`POST /api/aqua/wq/backfills`，requestId 强制幂等）：**只能追加一行，绝不覆盖原采样值**；重复补录请求返回首次记录。
- 主管审核（`POST /api/aqua/wq/tasks/{id}/review`）：驳回必须填意见且退回草稿；**审核通过后已测值才进入池塘健康曲线**（`GET /api/aqua/wq/ponds/{pondId}/health-curve`，缺测项不入曲线，取原值不取补录值）；重复审核不重复写曲线。
- 所有修改保留证据：生成/暂存/提交/补录/驳回/通过/版本切换均写 `t_wq_change_evidence`（含操作前后快照与幂等请求号），可经 `GET /api/aqua/wq/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=WaterQualitySamplingAcceptanceTest`，14 个检查点逐段输出报告，失败即停并给出停点。

## 饲料批号与配方投喂（aqua-3）

模块包路径 `com.evops.aqua.feed`，在台账之上打通：饲料批号三量账 → 配方按苗种/日龄/水温版本化 → 投喂单准备即冻结配方快照 → 锁定占用批号 → 临时减量审批 → 执行转消耗，日汇总试喂与正式分列，全程留证。

- 饲料批号（`POST /api/aqua/feed/batches`）：一批一行三量账（入库 / 锁定占用 / 已消耗），可用量=入库−锁定−已消耗；批号不可删除。锁定时按 **FEFO 先到期先用** 从未过期批号占用，可跨批号拼配；**批号已过期或可用库存不足一律拦截，且不残留半占用**（同事务回滚）。
- 投喂配方版本（`POST /api/aqua/feed/formulas`）：按苗种发布，版本下以「日龄闭区间 + 水温闭区间」配置饲料与计划投喂量，命中多行取区间最窄（最具体）者。**配方调整发布新版本，同苗种旧版本自动停用**；投喂单建立即冻结当时版本指针与计划量快照，**历史投喂永远引用旧版本快照，不受新版本影响**。
- 投喂单（`/api/aqua/feed/orders`）：`POST /prepare` 按池按批按日，日龄由投苗日与投喂日推算（不由前端填写），区分 `FORMAL` 正式投喂 / `TRIAL` 试喂，同池同批同日同类型幂等；`POST /{id}/lock` 按 FEFO 占用批号；**`POST /{id}/execute` 只有锁定后才允许执行**（草稿态直接执行拒绝），执行把锁定占用转为批号实际消耗，重复执行幂等；`POST /{id}/unlock` 释放占用退回草稿。
- 临时减量（`POST /api/aqua/feed/orders/{id}/items/{itemId}/reduction`）：仅锁定态可登记，实际量必须小于锁定量，**必须登记减量原因与审批人**，登记后释放差额占用，执行按减量后实际量消耗。
- 日汇总（`GET /api/aqua/feed/orders/daily-summary`）：按池按日只统计已执行投喂，**试喂与正式投喂严格分列、绝不混算**。
- 海水养殖管理权限（`/api/aqua/feed/access`）：`POST /tighten` 收紧、`POST /relax` 放开（均须登记原因）。**收紧后饲料批号停止推进**——新批号入库、锁定占用、执行消耗一律拦截；**已确认的配方版本与历史投喂事实保留不变**。
- 全程留证：入库/锁定/解锁/减量/执行/版本停用/权限收紧放开均写 `t_feed_change_evidence`（含操作前后 JSON 快照与幂等请求号），可经 `GET /api/aqua/feed/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=FeedFormulaFeedingAcceptanceTest`，17 个检查点逐段输出报告，失败即停并给出停点。

## 疫病观察与隔离池调度（aqua-4）

模块包路径 `com.evops.aqua.disease`，在台账之上打通：登记隔离池档案 → 发现异常症状生成观察单 → 按风险等级推荐隔离池 → 调度前校验 → 调入隔离池 → 兽医复核解除回原池，数量逐笔记录，定时复查任务幂等，全程留证且不丢操作者。

- 池塘隔离调度档案（`/api/aqua/disease/pond-guards`）：按池登记隔离池角色、容量、消毒有效期、调入权限，操作者必填，变更留证。
- 疫病观察单（`POST /api/aqua/disease/observations`，requestId 强制幂等）：发现异常症状即生成，记录症状、风险等级（LOW/MEDIUM/HIGH）与观察人；重复提交返回同一单。
- 按风险等级推荐隔离池（`GET /api/aqua/disease/observations/{id}/recommend-pools`）：低风险建议原地观察；中风险仅推荐同海区隔离池；高风险推荐全部可用隔离池（同海区优先）；被排除候选逐条说明原因。
- 隔离调度（`POST /api/aqua/disease/observations/{id}/dispatch`，requestId 幂等）：**转移前校验池容量、消毒状态、目标池调入权限与混养占用，失败时一次性说明全部冲突原因**并留失败证据（独立事务，不随业务回滚）；成功生成隔离单并同步台账转移记录。
- 隔离中管控：批次**禁止换池（混养）与出池**（台账接口直接拦截）；隔离池在隔离期间**禁止投苗混养**，他批不得再调入同一隔离池。
- 解除隔离（`POST /api/aqua/disease/isolations/{id}/release`）：**必须由兽医复核**——复核兽医与复核意见缺一不可；解除后批次回原池，重复解除幂等不多写。
- 数量逐笔账（`GET /api/aqua/disease/isolations/movements`）：原池与隔离池之间调入、回池各记一笔，数量与操作者随行，只追加。
- 定时复查任务（Quartz，cron 由 `aqua.disease.followup-scan-cron` 配置，默认每日 02:10）：按日扫描未结观察单生成复查任务，**task_no 按观察单+日期唯一——应用重启、任务重跑都不多写记录**；记录操作者固定为调度系统标识并保留原观察人，**不丢操作者**。`POST /api/aqua/disease/followup-tasks/scan` 可人工触发同一逻辑核查。
- 全程留证：档案变更/观察单/调度/调度失败/解除/复查任务均写 `t_disease_change_evidence`（含操作前后快照、幂等请求号与操作者），可经 `GET /api/aqua/disease/evidence` 追溯。

验收：`mvn test -Dtest=DiseaseIsolationAcceptanceTest`，15 个检查点逐段输出报告，失败即停并给出停点。




## 潮汐窗口换水排班（aqua-5）

模块包路径 `com.evops.aqua.tide`，打通：海区潮汐表导入 → 高潮前后偏移生成换水候选（半开区间）→ 减去禁作天气、交人员班次形成可执行窗口 → 设备互斥排班发布 → 修改走调度审批 / 自动顺延不改原计划 → 临时关闭通知班组并留回执，全程留证。

- 海区潮汐表（`POST /api/aqua/tide/tide-tables`）：按海区+日期导入高/低潮点，**同海区同日重复导入即覆盖**（先删后插、同事务留证）；潮点时刻必须落在潮汐日期当天。
- 候选展开规则（`POST /api/aqua/tide/window-rules`，按海区一行）：换水候选 = `[目标潮点时刻 − beforeMinutes, 目标潮点时刻 + afterMinutes)`。
- **统一时间口径（半开区间）**：候选、可执行窗口、禁作天气、班次、设备排班一律 `[start, end)`；端点相接不算重叠。归属日 `plan_date` 恒等于窗口**起始时刻所在自然日**，候选生成与窗口归属走同一个 `HalfOpenInterval` 口径——潮点被调到 00:00 等临界时刻重算，跨零点窗口仍归属起始日。
- 窗口生成（`POST /api/aqua/tide/windows/generate`）：候选**减去全部禁作天气时段**，再与**人员班次并集取交集**，短于最小时长的碎片不形成窗口；被排除候选逐条返回原因（全程禁作 / 无班次覆盖 / 时长不足）。相邻高潮候选若搭接自动合并。**重算只重建候选/可执行窗口，已排班、已关闭窗口冻结保留。**
- 换水排班（`POST /api/aqua/tide/schedules` → `POST /{id}/publish`）：仅可执行窗口能排班，作业时段必须落在窗口内且班组班次完整覆盖；**同一设备同一时间只能服务一口池**——半开重叠（SQL 粗筛 + 内存复核）即拒绝并一次性说明占用排班，**冲突失败证据独立事务留下（业务回滚证据不丢）**；设备可在窗口端点相接时立即服务下一口池。
- 发布后修改（`POST /api/aqua/tide/schedules/{id}/changes`，requestId 幂等）：**不直接改动，生成待调度审批单**；`POST /changes/{changeId}/approve` 审批——**REJECTED 必须填意见并维持原计划**，APPROVED 才按申请时段改期（仍校验设备互斥与窗口边界）。
- 自动顺延（`POST /api/aqua/tide/schedules/{id}/postpone`）：直接生效、不走审批，**只能向后顺延且永不改变 original_start/original_end（原计划永久保留）**；顺延仍受设备互斥与窗口边界约束。
- 临时关闭（`POST /api/aqua/tide/windows/{id}/close`，requestId 幂等）：关闭原因必填，窗口冻结、联动排班取消；**向全部受影响班组逐行发送通知**（`POST /api/aqua/tide/notices/{id}/ack` 回执，记录回执人与时间，重复回执不覆盖首次回执）。
- 全程留证：导入覆盖/窗口重算/排班准备/发布/修改申请/批准/驳回/顺延/设备冲突/关闭/通知/回执均写 `t_tide_change_evidence`（含操作前后 JSON 快照、幂等请求号与操作者），可经 `GET /api/aqua/tide/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=TideWindowSchedulingAcceptanceTest`，10 个检查点（CP0–CP9）逐段输出报告，含临界时间专项：直接用 SQL 把潮点调到 00:00、与天气禁作边界相切后重算，换水窗口仍按"半开起始时刻所在自然日"同一口径归属；失败即停并给出停点。
