package tn.elitetunisia.payment;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentReconciliationWorkerActivationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(DataSource.class, () -> Mockito.mock(DataSource.class))
            .withBean(PaymentReconciliationService.class, () -> Mockito.mock(PaymentReconciliationService.class))
            .withUserConfiguration(PaymentReconciliationWorker.class);

    @Test
    void workerIsDisabledWhenPropertyIsAbsent() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(PaymentReconciliationWorker.class));
    }

    @Test
    void workerIsDisabledWhenPropertyIsFalse() {
        runner.withPropertyValues("app.payment.reconciliation.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PaymentReconciliationWorker.class));
    }

    @Test
    void workerIsEnabledOnlyWhenPropertyIsTrue() {
        runner.withPropertyValues("app.payment.reconciliation.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(PaymentReconciliationWorker.class));
    }
}
