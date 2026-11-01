package com.placementhub;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlacementhubApplication {

	public static void main(String[] args) {
		// Run in UTC however the app is launched (Maven, VS Code, java -jar).
		// Windows reports "Asia/Calcutta", which Postgres rejects on connect.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(PlacementhubApplication.class, args);
	}

}
