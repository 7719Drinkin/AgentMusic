package com.agentmusic.agentmusic_backend.persistence.mybatis.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.agentmusic.agentmusic_backend.persistence.mybatis.mapper")
public class MybatisPersistenceConfig {
}
