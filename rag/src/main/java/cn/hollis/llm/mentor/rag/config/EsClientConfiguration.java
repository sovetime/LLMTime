package cn.hollis.llm.mentor.rag.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.net.ssl.SSLContext;

@Configuration
public class EsClientConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EsClientConfiguration.class);

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;

    @Value("${elasticsearch.scheme:https}")
    private String scheme;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Value("${elasticsearch.insecure:false}")
    private boolean insecure;

    @Bean
    @Lazy
    public RestHighLevelClient restHighLevelClient() {
        try {
            RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, scheme));

            // 如果需要 Basic Auth，配置 CredentialsProvider
            if (username != null && !username.isEmpty()) {
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(username, password));
                builder.setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    // 如果是 https 且 insecure=true，继续设置 SSLContext 和 HostnameVerifier
                    if ("https".equalsIgnoreCase(scheme) && insecure) {
                        try {
                            SSLContext sslContext = SSLContexts.custom()
                                    .loadTrustMaterial(null, (chain, authType) -> true) // trust all
                                    .build();
                            httpClientBuilder
                                    .setSSLContext(sslContext)
                                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to create SSLContext for ES client", e);
                        }
                    }
                    return httpClientBuilder;
                });
            } else {
                // 没有用户名，仅设置 insecure SSL（如果需要）
                if ("https".equalsIgnoreCase(scheme) && insecure) {
                    builder.setHttpClientConfigCallback(httpClientBuilder -> {
                        try {
                            SSLContext sslContext = SSLContexts.custom()
                                    .loadTrustMaterial(null, (chain, authType) -> true)
                                    .build();
                            httpClientBuilder
                                    .setSSLContext(sslContext)
                                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                            return httpClientBuilder;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to create SSLContext for ES client", e);
                        }
                    });
                }
            }

            return new RestHighLevelClient(builder);
        } catch (Exception e) {
            logger.warn("Failed to create Elasticsearch client: {}. ES functionality will be unavailable.", e.getMessage());
            return null;
        }
    }
}
