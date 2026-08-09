# Validation notes

The package was checked in the generation environment for:

- XML, JSON and YAML syntax.
- Java source parsing with JDK 21; no syntax-level Java errors were detected.
- Docker Compose YAML structure.
- Presence of all required source, migration, Docker, Nginx, CI and sample files.

A full Maven dependency build and npm dependency build could not be executed in the generation environment because external package registries were not reachable. Run the following after extraction to perform the definitive build verification:

```bash
docker compose build
# or
cd backend && mvn test
cd ../frontend && npm install && npm run build
```
