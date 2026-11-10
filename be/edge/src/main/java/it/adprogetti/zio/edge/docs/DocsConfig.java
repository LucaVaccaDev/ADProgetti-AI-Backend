package it.adprogetti.zio.edge.docs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocsProperties.class)
class DocsConfig {
}
