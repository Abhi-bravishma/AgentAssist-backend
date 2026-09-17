package com.agentassist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Warm-up makes a real OpenAI call on ApplicationReadyEvent; the context test
// must not depend on the network or spend tokens.
@SpringBootTest(properties = "rag.internal.embedding.warmup.enabled=false")
class AgentassistApplicationTests {

	@Test
	void contextLoads() {
	}

}
