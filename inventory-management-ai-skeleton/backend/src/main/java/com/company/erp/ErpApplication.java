package com.company.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động. Kế thừa {@link SpringBootServletInitializer} để triển khai WAR lên Tomcat 10.1 (D-39);
 * vẫn chạy được bằng {@code java -jar erp.war} khi phát triển.
 *
 * <p>Tắt người dùng mặc định của Spring Boot để không sinh và ghi log mật khẩu tự động;
 * người dùng thật do module {@code system} quản lý (Giai đoạn 1).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class ErpApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ErpApplication.class);
    }
}
