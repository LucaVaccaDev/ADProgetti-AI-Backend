package it.adprogetti.zio.edge.nas;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NasProperties.class)
class NasConfig {
}
