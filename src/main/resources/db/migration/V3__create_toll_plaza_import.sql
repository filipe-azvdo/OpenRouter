CREATE TABLE toll_plaza_import (
    id           UUID PRIMARY KEY,
    content_hash VARCHAR(64) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    inserted     INTEGER,
    reactivated  INTEGER,
    updated      INTEGER,
    deactivated  INTEGER,
    total_rows   INTEGER,
    errors       TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at  TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_toll_plaza_import_content_hash ON toll_plaza_import (content_hash);
