#!/bin/sh
# Seeds a locally running backend (SKIP_AUTH=true) with a few teams and reports,
# so both the admin and the normal-user view have something to explore.
#
# Run it once while the backend is started as admin (the default), then restart
# the backend with SKIP_AUTH_ROLE=user to see the same data as a normal user.
#
# Usage: scripts/seed-local.sh [base-url]   (default http://localhost:8787)

BASE="${1:-http://localhost:8787}/api"
JSON="Content-Type: application/json"

post() { curl -sf -X POST -H "$JSON" -d "$2" "$BASE/$1" || { echo "FAILED: POST $1" >&2; exit 1; }; }

# The normal user (dev-user@localhost) is a member of "Lokalt team" only.
post teams/new '{"name":"Lokalt team","email":"lokalt@localhost","members":["dev-user@localhost"]}' >/dev/null
post teams/new '{"name":"Andre team","email":"andre@localhost","members":[]}' >/dev/null

new_report() {
  post reports/new "{\"name\":\"$1\",\"urlTilSiden\":\"$2\",\"teamId\":\"$3\",\"isPartOfNavNo\":true}" |
    sed -n 's/.*"id" *: *"\([^"]*\)".*/\1/p'
}

new_report "Min tjeneste" "https://example.nav.no/min" lokalt-team >/dev/null
new_report "Søknadsskjema" "https://example.nav.no/soknad" lokalt-team >/dev/null
new_report "Andre teams side" "https://example.nav.no/andre" andre-team >/dev/null

OLD_ID=$(new_report "Gammel tjeneste" "https://example.nav.no/gammel" lokalt-team)
post "reports/$OLD_ID/archive" '{"reason":"Tjenesten er lagt ned"}' >/dev/null

echo "Seeded: teams 'Lokalt team' and 'Andre team', 4 reports (1 archived)."
