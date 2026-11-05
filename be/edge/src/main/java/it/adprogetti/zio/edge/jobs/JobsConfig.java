package it.adprogetti.zio.edge.jobs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JobsProperties.class)
class JobsConfig {
}
