#!/bin/sh
set -eu

for migration in /migrations/*.sql; do
    echo "Applying ${migration}"
    mysql \
        --protocol=TCP \
        --host=mysql \
        --user="${DB_USERNAME}" \
        "${MYSQL_DATABASE}" < "${migration}"
done

echo "Database schema is ready."
