package com.aiyi.game.dnfserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan({"com.aiyi"})
@EnableScheduling
public class DnfServerApplication {

	public static void main(String[] args) {
		// Set these before Spring or a connection pool performs the first lookup.
		// Channel DDNS must observe DNS changes rather than a stale JVM cache.
		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0");
		SpringApplication.run(DnfServerApplication.class, args);
	}

}
