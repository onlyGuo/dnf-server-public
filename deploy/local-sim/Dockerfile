ARG WEB_IMAGE=dnf-web:local
FROM python:3.13-slim AS supervisor
RUN pip install --no-cache-dir --target /supervisor supervisor==4.3.0
FROM ${WEB_IMAGE}

COPY --from=supervisor /supervisor/ /opt/dnf-sim-python/
ENV PYTHONPATH=/opt/dnf-sim-python
RUN printf '#!/bin/sh\nexec /usr/bin/python3 -m supervisor.supervisord "$@"\n' > /usr/bin/supervisord && \
    printf '#!/bin/sh\nexec /usr/bin/python3 -m supervisor.supervisorctl "$@"\n' > /usr/local/bin/supervisorctl && \
    chmod 755 /usr/bin/supervisord /usr/local/bin/supervisorctl && \
    mkdir -p /opt/dnf-sim /home/neople/game && cp /bin/bash /home/neople/game/df_game_r
COPY deploy/local-sim/ /opt/dnf-sim/
RUN chmod +x /opt/dnf-sim/*.sh
WORKDIR /app
EXPOSE 9001
ENTRYPOINT ["/opt/dnf-sim/start.sh"]
