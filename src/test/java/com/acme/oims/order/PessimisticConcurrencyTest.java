package com.acme.oims.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conc_pess;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "oims.reservation.stragtegy=pessimistic"})
public class PessimisticConcurrencyTest extends AbstractConcurrencyTest {

    @Test
    void exactlyTenReservationsNoNegativeStock() throws Exception {
        Outcome o = race();
        System.out.println("PESSIMISTIC->" + o);
        assertNeverOverSold(o);
        assertEquals(10, o.reserved());
        assertEquals(90, o.rejected());
        assertEquals(0, o.failed());
    }
}
