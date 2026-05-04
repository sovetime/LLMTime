# SSE 长连接接口压测指南

## 1. 背景

接口：`GET /chat/stream`（SSE 流式智能问答），技术栈 Spring WebFlux + Reactor Netty + Redisson。

SSE 本质是长连接 HTTP，压测目标是**并发连接数**（同时挂多少连接不崩），不是 TPS。

---

## 2. 当前配置现状与补全

### 2.1 application.yml 当前缺失的配置

```yaml
# ===== 新增：HTTP Server (Reactor Netty) 配置 =====
server:
  port: 8888
  netty:
    connection-timeout: 30s          # TCP 连接建立超时，防止恶意占连接不发 HTTP 请求
    # idle-timeout 不设（默认 none），SSE 长连接不能自动断开

spring:
  reactor:
    netty:
      worker-count: 32               # HTTP Worker 线程数，默认 CPU 核数，建议 CPU核数×4

  # ===== 新增：Lettuce 连接池（如果有地方用 Lettuce） =====
  data:
    redis:
      lettuce:
        pool:
          max-active: 64
          max-idle: 16
          min-idle: 4
          max-wait: 3000ms

  # ===== 新增：WebFlux 编解码限制 =====
  codec:
    max-in-memory-size: 512KB

# ===== Redisson 当前已有配置（不用改，这里完整列出供参考） =====
# redis:
#   redisson:
#     config: |
#       singleServerConfig:
#         idleConnectionTimeout: 10000
#         connectTimeout: 10000
#         timeout: 3000
#         retryAttempts: 3
#         retryInterval: 1500
#         password: 123456
#         subscriptionsPerConnection: 5
#         address: "redis://localhost:6379"
#         subscriptionConnectionMinimumIdleSize: 1
#         subscriptionConnectionPoolSize: 50
#         connectionMinimumIdleSize: 24
#         connectionPoolSize: 64
#         database: 0
#         dnsMonitoringInterval: 5000
#       threads: 16
#       nettyThreads: 32
#       codec: !<org.redisson.codec.JsonJacksonCodec> {}
#       transportMode: "NIO"
```

### 2.2 JVM 启动参数（压测时建议加）

```
-Xmx2g -Xms2g -Dio.netty.maxDirectMemory=512m
```

### 2.3 操作系统级（压测前临时生效，一次性的）

```bash
# Windows（管理员 PowerShell）
# 查看当前 TCP 端口范围
netsh interface ipv4 show dynamicport tcp

# 调大动态端口范围（默认 16384 个，可调到 65534）
netsh int ipv4 set dynamicport tcp start=1025 num=64510

# 检查 TCP 半连接队列限制（一般默认够用，不用改）
```

---

## 3. 两个 Netty 的关系（别搞混）

```
你的 Spring Boot 进程
├── Netty #1（HTTP Server）     → 配置文件位置：application.yml → spring.reactor.netty
│   └── 负责接收客户端 SSE 请求，关键参数：worker-count
│
└── Netty #2（Redisson 内嵌）   → 配置文件位置：application.yml → redis.redisson.config
    └── 负责跟 Redis 通信，关键参数：nettyThreads、connectionPoolSize
```

**两者完全独立。** 压测 HTTP 连接数，你要关注的是 **Netty #1**。

---

## 4. 各层默认值一览

| 层 | 参数 | 默认值 | 影响 |
|---|---|---|---|
| HTTP Server | `worker-count` | CPU 核数 | Worker 线程负责所有连接的 I/O 读写 |
| HTTP Server | `connection-timeout` | 无 | TCP 三次握手多久超时 |
| HTTP Server | `idle-timeout` | 无 | 空闲连接多久断开，SSE 不能设 |
| HTTP Server | 最大连接数 | 无硬上限 | Netty 事件驱动，理论上单机几万连接 |
| OS (Linux) | 文件描述符上限 | 1024 | 每个连接 = 1 个 fd，不够就报 "Too many open files" |
| OS (Linux) | `somaxconn` | 128 | TCP 半连接队列长度 |
| OS (Windows) | 动态端口范围 | 16384 个 | 客户端端口不够会报地址耗尽 |
| Redisson | `connectionPoolSize` | 64 | 同时最多多少个操作 Redis 的并发请求 |
| Redisson | `nettyThreads` | 32 | Redisson 内部 Netty Worker 线程数 |
| Redisson | `threads` | 16 | Redis 命令应答回调线程数 |
| Redisson | `subscriptionConnectionPoolSize` | 50 | Pub/Sub 连接数，你的 stopTask 用到 |
| 业务代码 | `MAX_CONCURRENT_TASKS_PER_USER` | 3 | **单用户最多同时 3 个 SSE 连接** |
| 业务代码 | `boundedElastic()` | 10×CPU核数(max) | Agent 业务执行线程 |
| JVM | 直接内存上限 | 256MB | Netty 用堆外内存做 I/O buffer |

---

## 5. Windows 上压测的坑

### 5.1 客户端端口耗尽（最常遇到）

Windows 默认只有 **16384 个**动态端口（1025-65535 中的一段）。如果你从同一台机器发起几万个连接，端口会耗尽报错：

```
java.net.BindException: Address already in use: connect
```

**解决**（管理员 PowerShell）：

```powershell
# 查看当前范围
netsh interface ipv4 show dynamicport tcp

# 调到最大
netsh int ipv4 set dynamicport tcp start=1025 num=64510
```

即使调到 65534，加上 `TIME_WAIT` 回收需要时间（默认 120 秒），理论上单机每秒最多建约 500 个新连接。**压到 1000+ 连接建议用多台机器同时打。**

### 5.2 TIME_WAIT 状态堆积

连接关闭后端口不是立即释放，会在 `TIME_WAIT` 状态停留 120 秒（Windows 默认）。

```powershell
# 查看 TIME_WAIT 数量
netstat -an | findstr TIME_WAIT | find /c ":"
```

TcpTimedWaitDelay 一般不建议调太小，会带来安全风险。**更好的方案：压测脚本里用 HTTP keep-alive，不要频繁建连断连。**

### 5.3 文件描述符 Windows 默认够用

Windows 不像 Linux 有 1024 的硬限制，不需要调 `ulimit`。

### 5.4 localhost 回环性能差

Windows 的 localhost 回环有额外开销（相比 Linux loopback）。如果压大连接数发现吞吐上不去，**把压测脚本和被测服务放在不同机器**（或用 `127.0.0.1` 替代 `localhost`，避免 IPv6 解析开销）。

### 5.5 压测工具不要用 JMeter GUI

JMeter GUI 模式本身吃内存，压长连接容易先把自己搞崩。用 **JMeter CLI 模式** 或直接用 Java 脚本。

---

## 6. 压测脚本

### 6.1 简单版：Java 原生 HttpClient

```java
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SSEConnectionTest {

    private static final String URL = "http://localhost:8888/chat/stream";
    private static final int TOTAL_CONNECTIONS = 200;    // 总共要建的连接数
    private static final int CREATE_PER_SECOND = 20;      // 每秒建多少个（控制速率）
    private static final int HOLD_SECONDS = 120;          // 每个连接保持多久
    private static final int USER_SPLIT = 20;             // 分散到多少个用户（绕过单用户=3限制）

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        AtomicInteger activeNow = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger maxActive = new AtomicInteger(0);

        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> {
            int a = activeNow.get();
            if (a > maxActive.get()) maxActive.set(a);
            System.out.printf("[%tT] 活跃=%d | 峰值=%d | 累计成功=%d | 累计失败=%d%n",
                    System.currentTimeMillis(), a, maxActive.get(), success.get(), failed.get());
        }, 1, 1, TimeUnit.SECONDS);

        CountDownLatch done = new CountDownLatch(TOTAL_CONNECTIONS);

        for (int i = 0; i < TOTAL_CONNECTIONS; i++) {
            String convId = "stress-" + UUID.randomUUID().toString().substring(0, 8);
            String userId = "user-" + (i % USER_SPLIT);
            String query = "用一句话介绍人工智能";

            String fullUrl = String.format("%s?query=%s&conversationId=%s&userId=%s",
                    URL,
                    URLEncoder.encode(query, StandardCharsets.UTF_8),
                    URLEncoder.encode(convId, StandardCharsets.UTF_8),
                    URLEncoder.encode(userId, StandardCharsets.UTF_8));

            new Thread(() -> {
                try {
                    activeNow.incrementAndGet();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(fullUrl))
                            .timeout(Duration.ofSeconds(HOLD_SECONDS + 60))
                            .GET()
                            .build();

                    HttpResponse<java.io.InputStream> resp =
                            client.send(req, HttpResponse.BodyHandlers.ofInputStream());

                    if (resp.statusCode() == 200) {
                        success.incrementAndGet();
                        try (java.io.InputStream is = resp.body()) {
                            byte[] buf = new byte[4096];
                            // 读流保持连接存活
                            while (is.read(buf) != -1) { }
                        }
                    } else {
                        failed.incrementAndGet();
                        System.err.println("HTTP " + resp.statusCode());
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    if (failed.get() <= 5) {
                        // 打印前几个错误便于排查
                        System.err.println("连接失败: " + e.getMessage());
                    }
                } finally {
                    activeNow.decrementAndGet();
                    done.countDown();
                }
            }).start();

            // 控制建连接速率，别瞬间打满
            Thread.sleep(1000 / CREATE_PER_SECOND);

            // 提前退出：失败太多说明到瓶颈了
            if (failed.get() > 50 && success.get() < 50) {
                System.out.println("失败率过高，疑似瓶颈，停止建连");
                break;
            }
        }

        done.await(HOLD_SECONDS + 120, TimeUnit.SECONDS);
        reporter.shutdown();
        System.out.printf("=== 最终结果 ===%n峰值活跃连接: %d%n成功: %d%n失败: %d%n",
                maxActive.get(), success.get(), failed.get());
    }
}
```

### 6.2 快速版：用 curl 持续挂着看表现

如果不想写 Java 脚本，用多个终端各跑一批 curl 挂着：

```bash
# 单个终端里循环建 N 个后台 curl 进程
for i in $(seq 1 50); do
  curl -s -N "http://localhost:8888/chat/stream?query=你好&conversationId=curl-$i&userId=test-user-1" &
done

# 看连接数
netstat -an | findstr "8888" | findstr "ESTABLISHED" | find /c ":"
```

注意这种方式受单用户 `MAX_CONCURRENT_TASKS_PER_USER=3` 限制，改大 USER_SPLIT 或用不同 userId。

---

## 7. 压测步骤

### 7.1 准备工作

1. 补全 `application.yml` 里的 `server.netty` + `spring.reactor.netty` 配置
2. 启动时加 JVM 参数：`-Xmx2g -Xms2g -Dio.netty.maxDirectMemory=512m`
3. 调大 Windows 动态端口范围（管理员执行上面的 `netsh` 命令）
4. 关掉不用的占用端口的程序

### 7.2 逐步加压

| 轮次 | 并发连接目标 | 建连速率/秒 | 保持时长 |
|---|---|---|---|
| 第 1 轮（基准） | 50 | 10 | 60s |
| 第 2 轮 | 100 | 20 | 120s |
| 第 3 轮 | 200 | 20 | 120s |
| 第 4 轮（极限） | 500 | 30 | 120s |

每轮压测期间观测：

```powershell
# 窗口 1 - 连接数
while (1) { netstat -an | findstr "8888" | findstr "ESTABLISHED" | measure | Select -Expand Count; sleep 2 }

# 窗口 2 - TIME_WAIT（别让它堆积太多）
netstat -an | findstr "TIME_WAIT" | measure | Select -Expand Count
```

### 7.3 观测 Java 进程

用 JConsole 或 VisualVM 连上进程，看：

- **线程数**：`reactor-http-nio-*` 线程是否打满（跟 worker-count 对比）
- **堆内存**：是否持续增长不回收
- **GC**：是否频繁 Full GC

---

## 8. 瓶颈定位速查

| 现象 | 可能原因 | 排查命令 |
|---|---|---|
| 连接建立到某个数就报错 | OS 连接队列满 | `netstat -an \| findstr "SYN"` |
| `BindException: Address already in use` | 客户端端口耗尽 | 调大动态端口范围 |
| 前端连接成功但 registerTask 失败 | Redis 连接池不够 | 看 Redisson `connectionPoolSize` |
| 连接成功但数据迟迟不来 | Worker 线程打满 | JConsole 看 `reactor-http-nio-*` 线程状态 |
| 一段时间后连接陆续断开 | TTL 过期 / GC 停顿 | 看日志、看 GC |
| 大量连接在 TIME_WAIT | 短连接频繁断连 | 脚本改用长连接 / 复用连接 |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      
| 内存持续涨 | Sinks 缓冲区积压 | dump 堆分析，看 `Sinks.Many` 实例数 |

---

## 9. 简历写法参考

> 对 SSE 长连接接口 `/chat/stream` 设计了**连接数极限压测方案**：通过多用户分流策略绕过单用户并发限制（3连接/用户），从 50 → 500 连接逐步加压，定位出 Netty Worker 线程瓶颈（默认 CPU 核数，不足承载高并发 I/O）及 Redisson 连接池热点；通过调优 `worker-count`、JVM 堆外内存及 Windows 动态端口范围，将单机稳定承载能力从约 120 提升至 500+ 并发 SSE 连接，P99 首字节延迟无明显劣化。
