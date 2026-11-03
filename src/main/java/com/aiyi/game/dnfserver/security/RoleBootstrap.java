package com.aiyi.game.dnfserver.security;

import com.aiyi.game.dnfserver.entity.AccountVO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.*;

/** Serializes initial administrator selection across app instances, including MyISAM accounts tables. */
@Component
public class RoleBootstrap {
    private static final String LOCK = "dnf-web-first-administrator";
    private final DataSource source;

    public RoleBootstrap(DataSource source) { this.source = source; }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() throws SQLException {
        try (Connection c = source.getConnection()) {
            acquire(c);
            try (Statement st = c.createStatement()) {
                boolean hasSuper;
                try (ResultSet rs = st.executeQuery("SELECT UID FROM d_taiwan.accounts WHERE admin=2 LIMIT 1")) {
                    hasSuper = rs.next();
                }
                if (!hasSuper) {
                    Long first = null;
                    try (ResultSet rs = st.executeQuery("SELECT UID FROM d_taiwan.accounts ORDER BY UID LIMIT 1")) {
                        if (rs.next()) first = rs.getLong(1);
                    }
                    if (first != null) try (PreparedStatement update = c.prepareStatement("UPDATE d_taiwan.accounts SET admin=2 WHERE UID=?")) {
                        update.setLong(1, first);
                        update.executeUpdate();
                    }
                }
            } finally { release(c); }
        }
    }

    /** Must be called inside the registration transaction before any account lookup. */
    public boolean assignRegistrationRole(AccountVO account, boolean gmRegistration) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("注册必须在事务中执行");
        }
        Connection c = DataSourceUtils.getConnection(source);
        try {
            acquire(c);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    try { release(c); } catch (SQLException ignored) { }
                }
            });
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT UID FROM d_taiwan.accounts ORDER BY UID LIMIT 1")) {
                boolean first = !rs.next();
                account.setAdmin(first ? 2 : gmRegistration ? 1 : 0);
                return first;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("无法确定账号权限", e);
        }
    }

    private static void acquire(Connection c) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT GET_LOCK(?, 15)")) {
            st.setString(1, LOCK);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) throw new SQLException("获取账号初始化锁超时");
            }
        }
    }

    private static void release(Connection c) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            st.setString(1, LOCK);
            st.executeQuery().close();
        }
    }
}
