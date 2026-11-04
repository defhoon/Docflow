package com.docflow.document.preview;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 미리보기 설정 바인딩 (SPEC-05 §8.3).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PreviewProperties.class)
public class PreviewConfig {

}
