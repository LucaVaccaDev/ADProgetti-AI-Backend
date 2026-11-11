package it.adprogetti.zio.edge.tenders;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ TedProperties.class, EuropaconcorsiProperties.class })
class TendersConfig {
}
