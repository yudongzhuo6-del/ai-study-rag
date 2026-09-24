package com.yudong.aistudy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "rag.vector-index.initialize-on-startup=false")
class AiStudyRagApplicationTests {

	@Test
	void contextLoads() {
	}

}
