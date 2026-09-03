# accessibility-reporting-tool

App for rapportering av tilgjengelighet fra team på NAV sine sider. For api docs se /static/openapi

## Utvikling
### Forutsetninger
- mvn er installert 3.9.0+ eller 4.0++
- docker er installert og kjører
- docker-compose er installert
- Java 21 er installert

### Bygge prosjektet
```
mvn clean install
```

### Could not find a valid Docker environment når du prøver å kjøre tester på mac (med colima)
1. `sudo ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
2. `colima stop`
3. `colima start --network-address`
4. Sett `ADMIN_GROUP=test_admin` i miljøet når du kjører lokale tester slik at admin-endepunktene fungerer i testene.
5. Logg alle innkommende HTTP-kall ved å sette `CALL_LOGGING_LEVEL=DEBUG` hvis du trenger ekstra feilsøking.

Default verdi på dev-logging er DEBUG, kan endres i [logback-dev.xml](app/src/main/resources/logback-dev.xml)

### Kjøre appen lokalt

1. start avhengigheter:
   ```
   docker-compose up
   ```
   Dette starter:
   - postgres database (port 5432)
   - mock oauth2 server (port 8080)

2. start appen med `SKIP_AUTH=true` (hopper over JWT-validering og injiserer en mock-admin-bruker — kun gyldig sammen med `ENV=local`, appen nekter å starte ellers):
   ```
   SKIP_AUTH=true CORS_ALLOWED_ORIGIN=* DB_HOST=localhost DB_PORT=5432 DB_DATABASE=a11y DB_USERNAME=postgres DB_PASSWORD=a11y ADMIN_GROUP=test_admin ADMINS=carl@good.morning AZURE_APP_CLIENT_ID=a11y AZURE_APP_WELL_KNOWN_URL=http://localhost:8080/issueissue/.well-known/openid-configuration PORT=8787 ENV=local mvn exec:java -Dexec.mainClass=accessibility.reporting.tool.AppKt
   ```

3. API-et er tilgjengelig på `http://localhost:8787` — klart til å brukes fra frontend (`pnpm dev` i [reops-uurapport-frontend](../reops-uurapport-frontend))

4. optional, test api-et direkte med token fra mock oauth2 (krever at appen kjøres UTEN `SKIP_AUTH`):
   ```
   curl -X POST http://localhost:8080/issueissue/token \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     -d 'grant_type=client_credentials' \
     -d 'client_id=a11y' \
     -d 'client_secret=ignored' \
     -d 'scope=a11y'

   curl http://localhost:8787/api/teams \
     -H "Authorization: Bearer <ditt-token>"
   ```

#### Example miljøvariabler for lokal kjøring
```
SKIP_AUTH=true;ADMIN_GROUP=test_admin;ADMINS=carl@good.morning;AZURE_APP_CLIENT_ID=a11y;AZURE_APP_WELL_KNOWN_URL=http://localhost:8080/issueissue/.well-known/openid-configuration;CALL_LOGGING_LEVEL=DEBUG;CORS_ALLOWED_ORIGIN=*;DB_DATABASE=a11y;DB_HOST=localhost;DB_PASSWORD=a11y;DB_PORT=5432;DB_USERNAME=postgres;ENV=local;PORT=8787
```

### Oppdatere apidocs
Apiet er beskrevet i filen [documentation.yaml](app/src/main/resources/static/openapi/documentation.yaml)
Du kan oppdatere manuelt eller bruke en plugin (f.eks openapi generator for ktor i intellij)
