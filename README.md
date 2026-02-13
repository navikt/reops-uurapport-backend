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
1. start docker-compose `docker-compose up` som starter opp:
   - postgres database
   - mock oauth2 server
   - redis
   - wonderwall (for å mocke kall til azure)
2. sett miljøvariablene (eksempel lenger nede på verdier):
   - AZURE_APP_CLIENT_ID
   - AZURE_APP_WELL_KNOWN_URL
   - ADMINS (komma-separert liste med e-poster for admin-tilgang)
   - ADMIN_GROUP (Azure-gruppe ID for admin-tilgang, default 'test_admin' lokalt)
   - DB_HOST
   - DB_PORT
   - DB_DATABASE
   - DB_USERNAME
   - DB_PASSWORD
   - CORS_ALLOWED_ORIGIN
   - PORT (default 8081)
   - ENV
3. se appen på `http://localhost:8787`
4. optional, generer token for å kalle api-et:
    ```
    curl -X POST http://localhost:8080/issueissue/token \
      -H 'Content-Type: application/x-www-form-urlencoded' \
      -d 'grant_type=client_credentials' \
      -d 'client_id=a11y' \
      -d 'client_secret=ignored' \
      -d 'scope=a11y' 
    ```
5. bruk token i kall til api-et:
    ```
      curl http://localhost:8787/api/teams \
        -H "Authorization: Bearer <ditt-token>'    
    ```
#### Example miljøvariabler for lokal kjøring
```
ADMIN_GROUP=test_admin;ADMINS=carl@good.morning;AZURE_APP_CLIENT_ID=a11y;AZURE_APP_WELL_KNOWN_URL=http://localhost:8080/issueissue/.well-known/openid-configuration;CALL_LOGGING_LEVEL=DEBUG;CORS_ALLOWED_ORIGIN=*;DB_DATABASE=a11y;DB_HOST=localhost;DB_PASSWORD=a11y;DB_PORT=5432;DB_USERNAME=postgres;ENV=local;PORT=8787
```

### Oppdatere apidocs
Apiet er beskrevet i filen [documentation.yaml](app/src/main/resources/static/openapi/documentation.yaml)
Du kan oppdatere manuelt eller bruke en plugin (f.eks openapi generator for ktor i intellij)
