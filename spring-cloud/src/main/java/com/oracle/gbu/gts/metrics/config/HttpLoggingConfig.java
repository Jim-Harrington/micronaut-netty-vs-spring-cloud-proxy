package com.oracle.gbu.gts.metrics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Enables spring http logging filter.
 */
@Configuration
public class HttpLoggingConfig {

  private static final Logger log = LoggerFactory.getLogger(HttpLoggingConfig.class);
    
    
}
