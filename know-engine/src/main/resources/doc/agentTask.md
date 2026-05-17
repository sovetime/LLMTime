AgentTaskManager 核心流程梳理
整体定位
这是一个多实例部署下的流式任务生命周期管理器，解决的核心问题是：用户点击"停止生成"时，请求可能打到任意一台实例，而任务只在某一台实例上运行。

核心数据结构
本地 ConcurrentHashMap<conversationId, TaskInfo>  ← 只存当前实例的任务
Redis RBucket  agent:task:{conversationId} = instanceId  ← 全局标记任务归属
Redis RTopic   agent:stop  ← 跨实例停止广播

主要流程
① 注册任务（registerTask）
本地 map 有？→ 拒绝（同实例重复）
↓
Redis trySet(instanceId, TTL=30min) 成功？→ 失败则说明其他实例在跑，拒绝
↓
写入本地 map，返回 TaskInfo
关键点：trySet 是原子操作，天然做了分布式互斥。

② 停止任务（stopTask）
这是整个类最核心的流程：
本地 map 有这个 conversationId？
├── 有 → 直接本地停止（快速路径，省一次网络）
└── 没有
├── Redis key 不存在 → 任务已结束，return false
├── Redis holder == 本实例 → 逻辑异常，跳过广播
└── holder 是其他实例 → Pub/Sub 广播 conversationId
↓
持有任务的实例收到消息 → handleRemoteStop → doStopTask

③ doStopTask（实际停止动作）
1. disposable.dispose()       → 中断底层 WebFlux/Reactor 流
2. sink.tryEmitNext(停止消息)  → 推送"⏹ 用户已停止"给前端 SSE
3. sink.tryEmitComplete()     → 关闭 SSE 连接
4. doRemoveTask()             → 清本地 map + 删 Redis key

④ TTL 刷新（防止长任务 key 过期）
每 5 分钟扫描本地 taskMap，对仍归属本实例的 Redis key 续期 30 分钟。如果发现 key 归属已变（被其他实例抢占或异常），主动清理本地记录。

面试高频追问
Q：为什么不直接用 Redis 做停止，而要用 Pub/Sub？
轮询 Redis 有延迟且浪费连接，Pub/Sub 是推模型，毫秒级响应，且只有持有任务的实例会响应，其他实例的 handleRemoteStop 拿不到 taskInfo 直接 return。
Q：stopTask 里为什么先判断本地再判断 Redis？
本地判断是纯内存操作，绝大多数负载均衡配置下（粘性会话）任务和停止请求会打到同一实例，走本地路径避免一次 Redis 网络 RTT。
Q：分布式场景下如何防止两个实例同时注册同一个会话的任务？
RBucket.trySet() 底层是 Redis SET NX EX 原子命令，只有一个实例能拿到，另一个直接返回 false 拒绝。
Q：销毁时（destroy）做了什么？
移除 Pub/Sub 监听器、关闭 TTL 刷新线程、遍历本地 taskMap 清理所有 Redis key，防止实例下线后 key 残留阻塞其他实例注册同会话新任务。