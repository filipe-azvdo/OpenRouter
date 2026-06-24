package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V4__toll_plaza_import_partial_unique_hash extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute("DROP INDEX IF EXISTS ux_toll_plaza_import_content_hash");

            String dbName = context.getConnection().getMetaData().getDatabaseProductName();
            if (dbName.contains("PostgreSQL")) {
                stmt.execute("CREATE UNIQUE INDEX ux_toll_plaza_import_content_hash "
                        + "ON toll_plaza_import (content_hash) "
                        + "WHERE status <> 'FAILED'");
            } else {
                stmt.execute("CREATE UNIQUE INDEX ux_toll_plaza_import_content_hash "
                        + "ON toll_plaza_import (content_hash)");
            }
        }
    }
}
