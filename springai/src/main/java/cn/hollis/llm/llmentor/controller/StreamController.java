package cn.hollis.llm.llmentor.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 流式输出控制器demo
 * 演示三种不同的服务端推送技术：SSE、StreamingResponseBody、WebFlux
 */
@RestController
@RequestMapping("/stream")
public class StreamController {

    /** DashScope API Key */
    private static final String API_KEY = "sk-18a5bc975dec45a5aaf484a48b7e600a";
    /** DashScope 兼容模式的 Chat Completions 接口地址 */
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /**
     * 模拟流式请求（实际返回非流式响应）
     * 向 DashScope API 发送流式请求，但由于使用 HttpClient 同步方式，响应为完整 JSON
     * @return API 返回的完整响应体
     */
    @RequestMapping("/fakeStream")
    public String fakeStream() {
        String requestBody = """
                {
                    "model": "glm-4.7",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a helpful assistant."
                        },
                        {
                            "role": "user",
                            "content": "你是什么模型"
                        }
                    ],
                    "stream": true
                }
                """;
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .header("X-DashScope-SSE", "enable")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return response.body();
    }

    /**
     * SSE（Server-Sent Events）流式输出示例
     * 使用 Spring 的 SseEmitter 推送服务端事件
     * @return SseEmitter 实例，超时时间为 60 秒
     */
    @GetMapping("/sse")
    public SseEmitter sse() {
        SseEmitter emitter = new SseEmitter(60_000L);
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    emitter.send("Message " + i);
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * StreamingResponseBody 流式输出示例
     * 直接向响应流写入数据，适合自定义流式协议
     * @return ResponseEntity，包含 StreamingResponseBody 和 SSE 内容类型头
     */
    @GetMapping("/entity")
    public ResponseEntity<StreamingResponseBody> chat() {

        StreamingResponseBody body = outputStream -> {
            for (int i = 0; i < 1000; i++) {
                String data = "Message " + i + "\n";
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                try {
                    Thread.sleep(500); // 模拟延迟
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(body);
    }

    /**
     * WebFlux 响应式流式输出示例
     * 使用 Project Reactor 的 Flux 实现真正的响应式流
     * @return 包含字符串消息的 Flux，每秒发射一条
     */
    @GetMapping(value = "/flux")
    public Flux<String> fluxStream() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(seq -> "Message " + seq);
    }

}