package cn.net.tomatoegg.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "cn.net.tomatoegg.ai")
@ConfigurationPropertiesScan
public class AiQAApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiQAApplication.class, args);
    }

}
