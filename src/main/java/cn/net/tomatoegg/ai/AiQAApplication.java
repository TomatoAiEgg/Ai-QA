package cn.net.tomatoegg.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.net.tomatoegg.ai")
public class AiQAApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiQAApplication.class, args);
    }

}
