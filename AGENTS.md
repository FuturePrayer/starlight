# AGENTS.md

## Project Conventions

- Backend: Java 25, Spring Boot 4, Maven. There is no Maven wrapper, use `mvn`.
- Frontend: Vue 3 + Vite + Pinia in `frontend/`. Use `npm` with `package-lock.json`; do not switch to pnpm or yarn.
- Frontend UI must remain theme-aware. New colors should come from existing CSS variables in `frontend/src/styles/variables.css` / `frontend/src/stores/theme.js`, or from `color-mix()` based on those variables. Avoid hardcoded colors unless they are truly semantic constants such as white text on a primary button.
- Backend changes should stay friendly to GraalVM Native Image even when building the normal JVM package. Avoid unnecessary reflection, dynamic class loading, and third-party libraries that rely heavily on reflection unless they have official or well-known community native-image support.

## Validation

- Frontend changes: run `npm run build` from `frontend/`.
- Backend changes: run `mvn test` from the repo root.
- Combined JVM package: run `npm run build:combined` from `frontend/`, then `mvn -DskipTests clean package` from the repo root.
- Docker Compose now builds from source using `deploy/Dockerfile.compose`. If Docker is available, validate with `docker compose config` or `docker compose build`.
- If a required validation command cannot run in the current environment, report that clearly in the final response.

## Files And Boundaries

- Do not modify root `Dockerfile.*` files for Compose-only changes; release workflows use them. Put Compose-specific build logic in `deploy/Dockerfile.compose`.
- Do not commit or hand-edit generated/runtime directories: `data/`, `target/`, `frontend/dist/`, `frontend/node_modules/`, `src/main/resources/static/`.
- `src/main/resources/static/` is produced by `npm run build:combined`; only regenerate it when intentionally updating the combined package assets.
- Never add secrets, tokens, local credentials, or private environment files to the repo or documentation.

## Local Notes

- Default persistent data is under `./data` and should be treated as runtime state.
- The default Compose image tag is local: `starlight:local`.
- Published image instructions remain in the READMEs for users who do not want to build from source.
