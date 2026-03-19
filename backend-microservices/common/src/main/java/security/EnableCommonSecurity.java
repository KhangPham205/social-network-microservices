package security;

import java.lang.annotation.*;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(CommonSecurityConfig.class)
public @interface EnableCommonSecurity {}

@Configuration
@ComponentScan(basePackages = "security")
class CommonSecurityConfig {
  // Class rỗng dùng để kích hoạt ComponentScan cho package này
}
