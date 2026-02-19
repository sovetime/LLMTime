package cn.hollis.llm.mentor.mcp.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * HTTPS MCP 客户端服务
 * 提供两种连接方式：
 * 1. 非安全模式（Insecure）：绕过 SSL 证书校验，适用于开发调试环境
 * 2. 安全模式（Secure）：加载自定义 CA 证书进行 SSL 双向验证，适用于生产环境
 */
@Service
@Slf4j
public class HttpsMcpService {

    /**
     * 创建跳过 SSL 验证的 MCP 客户端（非安全模式）
     * <p>
     * ⚠️ 警告：该方式信任所有证书，存在中间人攻击风险，仅供本地测试使用，禁止在生产环境中使用。
     *
     * @param baseUrl  MCP 服务的基础 URL，例如 https://127.0.0.1:8443
     * @param endpoint SSE 端点路径，例如 /sse
     */
    public static void createInsecureHttpsClient(String baseUrl, String endpoint) {
        try {
            // 构建一个信任所有证书的 TrustManager，跳过服务端证书链校验
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        // 返回空数组，表示不限制受信任的 CA
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        // 客户端证书校验：此处不做任何验证
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        // 服务端证书校验：此处不做任何验证
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // 初始化 TLS SSLContext，注入自定义的 TrustManager（信任所有证书）
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // 禁用主机名验证（Hostname Verification），防止因域名不匹配导致握手失败
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null); // null 表示不进行主机名校验

            // 构建 HttpClient，注入不安全的 SSLContext 及 SSLParameters
            HttpClient.Builder httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .sslContext(sslContext)
                    .sslParameters(sslParameters);

            // 构建 HTTP 请求模板，设置目标 URI 及认证 Token
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Authorization", "Bearer abc123456789");

            // 构建基于 SSE 的 MCP Transport，绑定 HttpClient 与请求模板
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(endpoint)
                    .clientBuilder(httpClient)
                    .requestBuilder(requestBuilder)
                    .build();

            // 创建同步 MCP 客户端并完成初始化握手
            McpSyncClient mcp = McpClient.sync(transport).build();
            mcp.initialize();
            log.info("MCP Client 初始化成功");

        } catch (Exception e) {
            throw new RuntimeException("创建 Insecure MCP Client 失败", e);
        }
    }


    /**
     * 创建基于自定义 CA 证书的安全 MCP 客户端（安全模式 POC）
     * <p>
     * 通过加载指定的 CA 证书文件构建受信任的 TrustManager，
     * 仅信任由该 CA 签发的服务端证书，同时保留默认的主机名验证逻辑。
     *
     * @param baseUrl    MCP 服务的基础 URL，例如 https://127.0.0.1:8443
     * @param endpoint   SSE 端点路径，例如 /sse
     * @param caCertPath 自签 CA 证书的本地文件路径（PEM/DER 格式的 .crt 文件）
     */
    public static void createSecureHttpsClientPoc(String baseUrl, String endpoint, String caCertPath) {
        try {
            // 从磁盘加载 CA 证书文件，解析为 X.509 证书对象
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            FileInputStream fis = new FileInputStream(caCertPath);
            Certificate caCert = cf.generateCertificate(fis);
            fis.close();

            // 创建空的 KeyStore，并将 CA 证书以别名 "caCert" 导入
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null); // 不从文件加载，初始化为空
            ks.setCertificateEntry("caCert", caCert);

            // 使用包含自定义 CA 的 KeyStore 初始化 TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            // 基于 TrustManagerFactory 构建 SSLContext，仅信任自定义 CA 签发的证书
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

            // 构建 HttpClient，注入安全的 SSLContext；保留默认主机名验证
            HttpClient.Builder httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .sslContext(sslContext);

            // 构建 SSE Transport，绑定 HttpClient 与携带 Token 的请求头
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(endpoint)
                    .clientBuilder(httpClient)
                    .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer abc123456789"))
                    .build();

            // 创建同步 MCP 客户端并完成初始化握手
            McpSyncClient mcp = McpClient.sync(transport).build();
            mcp.initialize();
            log.info("MCP Client 初始化成功");

        } catch (Exception e) {
            throw new RuntimeException("创建 Secure MCP Client 失败", e);
        }
    }

    /**
     * 本地测试入口
     * 演示如何使用安全模式连接本地自签证书的 MCP Server
     */
    public static void main(String[] args) {
        // 使用安全模式，加载本地 CA 证书连接 HTTPS MCP Server
//        createInsecureHttpsClient("https://127.0.0.1:8443", "/sse"); // 非安全模式（仅调试用）
        createSecureHttpsClientPoc(
                "https://127.0.0.1:8443",
                "/sse",
                "D:\\time\\Project\\LLMTime\\mcp\\mcp-server-sse-https\\src\\main\\resources\\mcp-server.crt"
        );
    }
}