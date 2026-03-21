package cn.suhoan.startlight;

import cn.suhoan.startlight.config.StarlightProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StarlightProperties.class)
public class StartlightApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartlightApplication.class, args);
    }

}
