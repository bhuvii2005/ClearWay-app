package com.cleanroute.api.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Turns on the {@code clearway.ai.*} settings. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
