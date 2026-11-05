package com.aiyi.game.dnfserver.mail;

import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.entity.Postal;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PostalWriteTransactionTest {
    @Test public void mailAndNotificationRollbackTogether() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE fake_postal (character_id INT NOT NULL)");
        OnlineMailNotificationService notifications = new OnlineMailNotificationService(source, "/tmp", 3);
        assertTrue(notifications.ensureQueue());
        PostalDao postalDao = mock(PostalDao.class);
        when(postalDao.add(any(Postal.class))).thenAnswer(call -> {
            jdbc.update("INSERT INTO fake_postal(character_id) VALUES (1)");
            return 1;
        });
        PostalWriteTransaction writer = new PostalWriteTransaction();
        ReflectionTestUtils.setField(writer, "postalDao", postalDao);
        ReflectionTestUtils.setField(writer, "notifications", notifications);
        Postal postal = new Postal();
        postal.setReceiveCharacNo("1");
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        try {
            tx.execute(status -> { writer.write(postal, true); throw new IllegalStateException("rollback"); });
            fail("transaction should roll back");
        } catch (IllegalStateException expected) {
            assertEquals("rollback", expected.getMessage());
        }
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM fake_postal", Integer.class));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM dnf_mail_notification", Integer.class));
    }
}
