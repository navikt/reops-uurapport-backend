# accessibility-reporting-tool

App for rapportering av tilgjengelighet fra team på NAV sine sider. For api docs se /static/openapi

## Utvikling
### Forutsetninger
```
AZURE_APP_CLIENT_ID=a11y;AZURE_APP_WELL_KNOWN_URL=http://host.docker.internal:8080/issueissue/.well-known/openid-configuration;DB_DATABASE=a11y;DB_HOST=localhost;DB_PASSWORD=a11y;DB_PORT=5432;DB_USERNAME=postgres;CORS_ALLOWED_ORIGIN:*;ADMIN_GROUP=test_admin
```
* Appen kan nås på `http://localhost:3000`

Appen er satt opp med defaultverdier for mocked jwt som kan endres i definisjonen av mock-oauth2-server
i [docker-compose](docker-compose.yml)

```
"aud": ["a11y"],
"email" : "carl@good.morning",
"name": "Carl Good Morning",
"oid": "tadda-i-fixed-it"
```

Default verdi på dev-logging er DEBUG, kan endres i [logback-dev.xml](app/src/main/resources/logback-dev.xml)

### Kjøre appen lokalt
1. start docker-compose `docker-compose up` som starter opp:
   - postgres database
   - mock oauth2 server
   - redis
   - wonderwall (for å mocke kall til azure)
2. sett miljøvariablene:
   - AZURE_APP_CLIENT_ID
   - AZURE_APP_WELL_KNOWN_URL
   - ADMINS
   - DB_HOST
   - DB_PORT
   - DB_DATABASE
   - DB_USERNAME
   - DB_PASSWORD
   - CORS_ALLOWED_ORIGIN
   - PORT (default 8081)
3. se appen på `http://localhost:8787`
4. optional, generer token for å kalle api-et:
    ```
    curl -X POST http://host.docker.internal:8080/issueissue/token \
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
### Oppdatere apidocs
Apiet er beskrevet i filen [documentation.yaml](app/src/main/resources/static/openapi/documentation.yaml)
Du kan oppdatere manuelt eller bruke en plugin (f.eks openapi generator for ktor i intellij)

## Troubleshooting
### Unresolved Network Adress når du prøver å starte appen (mac)
1. Åpne /etc/host `open /etc/hosts`
2. Legg inn på ny linje: `127.0.0.1 host.docker.internal`

### Could not find a valid Docker environment når du prøver å kjøre tester på mac (med colima)

1. `sudo ln -s $HOME/.docker/run/docker.sock /var/run/docker.sock
2. `colima stop`
3. `colima start --network-address`
4. Sett `ADMIN_GROUP=test_admin` i miljøet når du kjører lokale tester slik at admin-endepunktene fungerer i testene.
5. Logg alle innkommende HTTP-kall ved å sette `CALL_LOGGING_LEVEL=DEBUG` hvis du trenger ekstra feilsøking.
