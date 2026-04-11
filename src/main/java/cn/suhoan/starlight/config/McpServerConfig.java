package cn.suhoan.starlight.config;

import cn.suhoan.starlight.service.McpAuthService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * MCP Server 自定义配置。
 * <p>通过自定义 transport 注入 API Key 鉴权与请求上下文。</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/api/mcp}") String mcpEndpoint,
            McpAuthService mcpAuthService) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(mcpEndpoint)
                .securityValidator(mcpAuthService::validateAndBind)
                .contextExtractor(request -> McpTransportContext.create(mcpAuthService.exportTransportMetadata()))
                .build();
    }

    @Bean(name = "webMvcStatelessServerRouterFunction")
    public RouterFunction<ServerResponse> webMvcStatelessServerRouterFunction(
            WebMvcStatelessServerTransport webMvcStatelessTransport) {
        return webMvcStatelessTransport.getRouterFunction();
    }
}

