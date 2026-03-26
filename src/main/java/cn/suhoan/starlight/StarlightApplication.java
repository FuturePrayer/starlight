package cn.suhoan.starlight;

import cn.suhoan.starlight.config.StarlightProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StarlightProperties.class)
public class StarlightApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarlightApplication.class, args);
    }

}
