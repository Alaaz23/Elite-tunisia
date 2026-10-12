package tn.elitetunisia;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class EarlyTestPropertySetter implements TestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        String usePg = System.getenv("ELITE_POSTGRES_TEST");
        if (usePg == null || usePg.isBlank()) {
            System.setProperty("spring.profiles.active", "test");
            System.setProperty("spring.datasource.url", "jdbc:h2:mem:elite_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            System.setProperty("spring.datasource.username", "sa");
            System.setProperty("spring.datasource.password", "");
            System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
            System.setProperty("spring.flyway.enabled", "false");
            System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        }
    }

}
