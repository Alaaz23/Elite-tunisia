package tn.elitetunisia;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Désactive, après chaque classe de test Spring, les comptes committés enregistrés par {@link TestIdentities}.
 * Le nettoyage s'exécute même si la classe a échoué, et ne touche jamais aux comptes non enregistrés.
 */
public class TestAccountCleanupListener implements TestExecutionListener {

    @Override
    public void afterTestClass(TestContext testContext) {
        TestIdentities.disableCreated(testContext.getApplicationContext().getBean(JdbcTemplate.class));
    }
}
