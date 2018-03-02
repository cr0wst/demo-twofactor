package net.smcrow.demo.twofactor;

import com.nexmo.client.NexmoClient;
import com.nexmo.client.auth.AuthMethod;
import com.nexmo.client.auth.TokenAuthMethod;
import com.nexmo.client.verify.VerifyClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class TwofactorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TwofactorApplication.class, args);
	}

	@Bean
	public NexmoClient nexmoClient(Environment environment) {
		AuthMethod auth = new TokenAuthMethod(
				environment.getProperty("nexmo.api.key"),
				environment.getProperty("nexmo.api.secret")
		);
		return new NexmoClient(auth);
	}

	@Bean
	public VerifyClient nexmoVerifyClient(NexmoClient nexmoClient) {
		return nexmoClient.getVerifyClient();
	}
}
