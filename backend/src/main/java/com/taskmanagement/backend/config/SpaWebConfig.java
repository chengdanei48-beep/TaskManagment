package com.taskmanagement.backend.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 同梱したフロントエンド(static/)を配信する。 /login のような画面のURLを直接開いても表示できるよう、該当するファイルがなければ index.html を返す。 ただし
 * /api/** と、拡張子付きの存在しないファイル(例: /assets/x.js)は index.html を返さず404にする。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String INDEX = "static/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(
                        new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location)
                                    throws IOException {
                                Resource requested = location.createRelative(resourcePath);
                                if (requested.exists() && requested.isReadable()) {
                                    return requested;
                                }
                                if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                                    return null;
                                }
                                Resource index = new ClassPathResource(INDEX);
                                return index.exists() ? index : null;
                            }
                        });
    }
}
