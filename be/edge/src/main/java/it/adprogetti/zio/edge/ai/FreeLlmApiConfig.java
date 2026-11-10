package it.adprogetti.zio.edge.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FreeLlmApiProperties.class)
class FreeLlmApiConfig {
}
