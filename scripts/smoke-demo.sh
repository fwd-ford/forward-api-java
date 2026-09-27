#!/usr/bin/env bash
# Smoke test of a running ForwardService API (demo profile): public endpoints, login,
# JWT on protected resources, dealer scoping, PATCH state machine, CRUD status codes,
# 401/403/409/422, token revocation and SOAP. Tokens are redacted in the output.
# Run it against a fresh demo instance: it deactivates atendente@forward.dev at the end.
#
# Usage: scripts/smoke-demo.sh [base_url]      (default http://localhost:8080)
# Requires: curl and python3 (or python).
#
# Teste de fumaca da API em execucao (perfil demo). Tokens sao ocultados na saida.
set -uo pipefail

BASE="${1:-http://localhost:8080}"
PASSWORD="Forward@2026"
PY="$(command -v python3 || command -v python)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pretty() {
  "$PY" -c '
import json, sys
raw = sys.stdin.read()
try:
    data = json.loads(raw)
except Exception:
    print(raw[:1500]); sys.exit(0)
def redact(o):
    if isinstance(o, dict):
        return {k: (v[:12] + "...(redacted)" if k == "access_token" else redact(v)) for k, v in o.items()}
    if isinstance(o, list):
        return [redact(x) for x in o]
    return o
data = redact(data)
if isinstance(data, list) and len(data) > 2:
    print(json.dumps(data[:2], ensure_ascii=False, indent=2))
    print("... (%d itens no total nesta pagina)" % len(data))
else:
    print(json.dumps(data, ensure_ascii=False, indent=2))
'
}

# call LABEL METHOD PATH TOKEN_VAR_NAME [JSON_BODY] [CONTENT_TYPE]
call() {
  local label="$1" method="$2" path="$3" token="${4:-}" body="${5:-}" ctype="${6:-application/json}"
  local args=(-s -o "$TMP/body" -D "$TMP/headers" -X "$method" "$BASE$path")
  local shown="curl -X $method $BASE$path"
  if [ -n "$token" ]; then
    args+=(-H "Authorization: Bearer ${!token}")
    shown="$shown -H 'Authorization: Bearer \$$token'"
  fi
  if [ -n "$body" ]; then
    args+=(-H "Content-Type: $ctype" --data "$body")
    shown="$shown -H 'Content-Type: $ctype' -d '$body'"
  fi
  echo "### $label"
  echo "\$ $shown"
  curl "${args[@]}"
  head -1 "$TMP/headers" | tr -d '\r'
  grep -iE '^(content-type|location|x-total-count|retry-after|www-authenticate|deprecation|link):' \
    "$TMP/headers" | tr -d '\r'
  if [ -s "$TMP/body" ]; then pretty < "$TMP/body"; fi
  echo
}

login() {
  curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" |
    "$PY" -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

echo "ForwardService API smoke test - $(date -u +%Y-%m-%dT%H:%M:%SZ) - $BASE"
echo

call "Health (publico)" GET /health
call "Sem token em rota protegida -> 401" GET /api/v1/leads
call "Login GESTOR (publico) -> 200 + JWT" POST /api/v1/auth/login "" \
  "{\"email\":\"gestor@forward.dev\",\"password\":\"$PASSWORD\"}"
call "Senha errada -> 401 AUTH_INVALID_CREDENTIALS" POST /api/v1/auth/login "" \
  '{"email":"gestor@forward.dev","password":"Errada@2026"}'

GESTOR_TOKEN="$(login gestor@forward.dev)"
ATENDENTE_TOKEN="$(login atendente@forward.dev)"
ADMIN_TOKEN="$(login admin@forward.dev)"

call "GET /me (GESTOR)" GET /api/v1/me GESTOR_TOKEN
call "Leads do GESTOR (so a propria concessionaria) + X-Total-Count" GET "/api/v1/leads?limit=3" GESTOR_TOKEN
call "Detalhe do lead" GET /api/v1/leads/a1000000-0000-4000-8000-000000000001 ATENDENTE_TOKEN
call "PATCH lead new -> contacted -> 200" PATCH /api/v1/leads/a1000000-0000-4000-8000-000000000001 \
  ATENDENTE_TOKEN '{"status":"contacted","notes":"Cliente atendeu, retorno amanha."}'
call "PATCH lead convertido -> lost -> 409" PATCH /api/v1/leads/a1000000-0000-4000-8000-000000000005 \
  ATENDENTE_TOKEN '{"status":"lost"}'
call "Lead de outra concessionaria -> 403" GET /api/v1/leads/a1000000-0000-4000-8000-000000000009 ATENDENTE_TOKEN
call "ATENDENTE em /users -> 403" GET /api/v1/users ATENDENTE_TOKEN
call "ADMIN lista usuarios" GET "/api/v1/users?limit=2" ADMIN_TOKEN
call "GESTOR cria evento de servico -> 201 + Location" POST /api/v1/service-events GESTOR_TOKEN \
  '{"vin":"9BFZZZ5SZJB000011","dealer_code":"F0001","service_code":1,"maintenance_number":4,"km":40100,"service_date":"2026-11-20T09:00:00-03:00","main_source":"dealer_app"}'
EVENT_ID="$("$PY" -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$TMP/body" 2>/dev/null || true)"
call "Evento duplicado -> 409" POST /api/v1/service-events GESTOR_TOKEN \
  '{"vin":"9BFZZZ5SZJB000011","dealer_code":"F0001","service_code":1,"maintenance_number":4,"km":40100,"service_date":"2026-11-20T09:00:00-03:00","main_source":"dealer_app"}'
call "Evento com VIN inexistente -> 422" POST /api/v1/service-events ADMIN_TOKEN \
  '{"vin":"9BFZZZ5SZJB999999","dealer_code":"F0001","service_code":1,"maintenance_number":1,"service_date":"2026-11-20T09:00:00-03:00","main_source":"dealer_app"}'
if [ -n "$EVENT_ID" ]; then
  call "GESTOR tenta excluir evento -> 403" DELETE "/api/v1/service-events/$EVENT_ID" GESTOR_TOKEN
  call "ADMIN exclui evento -> 204" DELETE "/api/v1/service-events/$EVENT_ID" ADMIN_TOKEN
fi
call "Metodo nao suportado -> 405" DELETE /api/v1/leads ADMIN_TOKEN
call "Score do cliente (sub-recurso)" GET /api/v1/customers/11111111-1111-1111-1111-111111111001/score ATENDENTE_TOKEN

call "ATENDENTE com token valido" GET /api/v1/me ATENDENTE_TOKEN
call "ADMIN desativa o atendente -> 200 (token_version incrementado)" PATCH   /api/v1/users/ad000000-0000-4000-8000-000000000003 ADMIN_TOKEN '{"active":false}'
call "Mesmo token do atendente -> 401 AUTH_TOKEN_REVOKED" GET /api/v1/me ATENDENTE_TOKEN

echo "### SOAP GetVehicle com token"
echo "\$ curl -X POST $BASE/soap/vehicles -H 'Content-Type: text/xml' -H 'Authorization: Bearer \$ADMIN_TOKEN' -d '<GetVehicleRequest>...'"
curl -s -X POST "$BASE/soap/vehicles" -H 'Content-Type: text/xml' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:v="urn:forwardservice:vehicles"><soapenv:Body><v:GetVehicleRequest><v:VIN>9BFZZZ5SZJB000001</v:VIN></v:GetVehicleRequest></soapenv:Body></soapenv:Envelope>' \
  -w '\nHTTP %{http_code}\n'
echo
echo "### WSDL (publico)"
curl -s -o /dev/null -w "GET /soap/vehicles.wsdl -> HTTP %{http_code}\n" "$BASE/soap/vehicles.wsdl"
