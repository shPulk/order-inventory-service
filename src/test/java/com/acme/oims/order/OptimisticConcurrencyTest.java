package com.acme.oims.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conc_pess;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "oims.reservation.stragtegy=optimistic",
        "oims.reservation.max-attempts=5"})
public class OptimisticConcurrencyTest extends AbstractConcurrencyTest{

    @Test
    void neverOverSellsEvenWhenRetriesRunOut() throws Exception {
        Outcome o = race();
        System.out.println("OPTIMISTIC->" +o);
        assertNeverOverSold(o);
        assertTrue(o.reserved() >= 1);
    }
}
