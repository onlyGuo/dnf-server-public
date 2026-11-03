# DNF Server Management Platform: Docker Guide

The platform and the game service are separate projects. You can deploy the platform on its own or use the integrated service image:

- Platform: `guoshengkai/dnf-server:latest`; the container listens on port 80 by default.
- Service with the platform: `guoshengkai/dnf-server:dnf-integrated`; the platform listens on port 9001 in bridge mode and port 8003 in host-network mode by default.

The published tags currently target `linux/amd64`. See the [project README](README.md) for standalone deployment and database configuration, and the [integration guide](deploy/integrated/README.md) for service networking, persistent data, and channel management.

The platform connects to an existing game database. Its container initializes missing tables and columns by default; set `DNF_DB_AUTO_INIT=false` to disable this. A standalone deployment does not generate a separate game key: configure an existing private key that matches the game's public key for client login.

When the platform can access the service directory and Supervisor, a super administrator can upload a matching RSA key pair or generate one. The platform synchronizes the keys and restarts the affected game processes. Channel and PVF management also require access to the service files and Supervisor.
