#!/bin/sh

# Init data directory
mkdir -p /app/data

# Login uses an existing platform or game key, or an explicitly configured key.
# Do not generate an unrelated key for a standalone Web instance.

# DB Initialization
MARKER_FILE="/app/data/.db_initialized"
if [ "${DNF_DB_AUTO_INIT:-true}" = "true" ] && [ ! -f "$MARKER_FILE" ]; then
    echo "DB Initialization marker not found. Attempting to initialize DB..."

    if [ -n "$SPRING_DATASOURCE_URL" ] && [ -n "$SPRING_DATASOURCE_USERNAME" ]; then
        # Parse Host and Port from JDBC URL (jdbc:mysql://host:port/db?params)
        # Remove protocol
        PROTO_REMOVED=${SPRING_DATASOURCE_URL#jdbc:mysql://}

        # Extract Host: take substring before first ':', '/', or '?'
        DB_HOST=$(echo "$PROTO_REMOVED" | sed -e 's|:.*||' -e 's|/.*||' -e 's|?.*||')

        # Extract Port: find digits after first ':'
        # If no port specified, sed returns empty
        DB_PORT=$(echo "$PROTO_REMOVED" | sed -n 's|.*:\([0-9]*\).*|\1|p')
        if [ -z "$DB_PORT" ]; then DB_PORT=3306; fi

        # Extract DB Name
        if echo "$PROTO_REMOVED" | grep -q "/"; then
             DB_NAME_EXTRACTED=$(echo "$PROTO_REMOVED" | cut -d/ -f2- | cut -d? -f1)
        fi
        export DB_NAME="${DB_NAME_EXTRACTED:-dnf_service}"

        echo "Connecting to MySQL at $DB_HOST:$DB_PORT (User: $SPRING_DATASOURCE_USERNAME, DB: $DB_NAME)..."

        # The source SQL also contains destructive DROP statements and sample data.
        # Generate and apply only additive schema changes against the configured DB.
        INIT_SQL=$(mktemp /tmp/dnf-web-init.XXXXXX)
        export DNF_DB_INIT_HOST="$DB_HOST" DNF_DB_INIT_PORT="$DB_PORT"
        export DNF_DB_INIT_USER="$SPRING_DATASOURCE_USERNAME" DNF_DB_INIT_PASSWORD="$SPRING_DATASOURCE_PASSWORD"
        export DNF_DB_INIT_NAME="$DB_NAME" DNF_INIT_SQL=/app/init.sql.template
        if python3 /app/init_db.py > "$INIT_SQL" && \
           MYSQL_PWD="$SPRING_DATASOURCE_PASSWORD" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$SPRING_DATASOURCE_USERNAME" < "$INIT_SQL"; then
            echo "Database initialized successfully."
            touch "$MARKER_FILE"
        else
            echo "WARNING: Database initialization failed. Please check logs above."
            echo "Connection params: Host=$DB_HOST, Port=$DB_PORT, User=$SPRING_DATASOURCE_USERNAME"
        fi
        rm -f "$INIT_SQL"
        unset DNF_DB_INIT_PASSWORD
    else
        echo "Skipping DB init: Missing SPRING_DATASOURCE_URL or USERNAME."
    fi
elif [ -f "$MARKER_FILE" ]; then
    echo "DB already initialized."
else
    echo "Automatic DB initialization disabled."
fi

# Substitute env vars in Nginx config
# We use only specific vars to avoid replacing $uri or other nginx variables
export DNF_NGINX_MAX_BODY_SIZE="${DNF_NGINX_MAX_BODY_SIZE:-33g}"
envsubst '${NGINX_PORT} ${SERVER_PORT} ${DNF_NGINX_MAX_BODY_SIZE}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

# Start Nginx
echo "Starting Nginx..."
service nginx start

# Start Java App
echo "Starting Java Application..."
# Pass system properties if needed, though ENV vars usually suffice for Spring Boot
exec java -jar /app/app.jar
