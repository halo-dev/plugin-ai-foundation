package run.halo.aifoundation.service.usage;

import java.sql.Connection;
import java.sql.SQLException;

/** Never return a connection with an uncertain transaction to auto-commit mode. */
final class UsageSqliteTransactions {

    private UsageSqliteTransactions() {
    }

    static boolean rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException | RuntimeException | LinkageError rollbackFailure) {
            if (failure != rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            try {
                connection.close();
            } catch (SQLException | RuntimeException | LinkageError closeFailure) {
                if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            return false;
        }
    }
}
