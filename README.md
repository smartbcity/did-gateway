# Token helper

## Keycloak config

### Enable token exchange

- Client creation
  - Enable client authentication
- Add external IDP
  - Add email mapper
    - Map userEmail claim (from waltid idp) to email keycloak attribute
- Policy creation
  - In the realm-management client, add a custom policy for the newly created client
  - In the IDP config, in the Permissions tab, add the created policy


### Example curl
```
curl --location 'http://did.fiware.smart-b.io:8080/realms/fiware-server/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
--data-urlencode 'requested_token_type=urn:ietf:params:oauth:token-type:access_token' \
--data-urlencode 'client_id=token-exchange' \
--data-urlencode 'client_secret=***REMOVED***' \
--data-urlencode 'subject_token=eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJYMk13cVhvQXU2VmFHMlNsU1RoSk0tVzQ1TmNiSldmYW96aHhuRE1Td2hVIn0.eyJleHAiOjE2OTE0NTQyNzksImlhdCI6MTY5MTQxODI3OSwianRpIjoiOTQ2NDIwNjItYTFhMy00MjIyLWI3NTAtN2I0NTVmODdkNzg3IiwiaXNzIjoiaHR0cHM6Ly9hdXRoLnNtYXJ0LWIuaW8vYXV0aC9yZWFsbXMvdmF1bHQtdGVzdCIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiI3YTgwYmRmMi00MDAwLTQ5ODUtOGZjZC1mOGQ4YWE0M2QzMjMiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJ2YXVsdCIsInNlc3Npb25fc3RhdGUiOiI0M2VmOWMwYy1kYmU0LTQ0ZjgtOGVjMi04MTVlYjg4ZTUxYTAiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtdmF1bHQtdGVzdCIsImltX3dyaXRlX3VzZXIiLCJ2YXVsdC11c2VyIiwiaW1fd3JpdGVfcm9sZSIsInN1cGVyX2FkbWluIiwib2ZmbGluZV9hY2Nlc3MiLCJpbV93cml0ZV9vcmdhbml6YXRpb24iLCJpbV9yZWFkX3VzZXIiLCJpbV9yZWFkX29yZ2FuaXphdGlvbiIsImltX3JlYWRfcm9sZSIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCIsInNpZCI6IjQzZWY5YzBjLWRiZTQtNDRmOC04ZWMyLTgxNWViODhlNTFhMCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJ0ZXN0IjoidGhpc2lzYXRlc3R2YWx1ZSIsInByZWZlcnJlZF91c2VybmFtZSI6InRlZGR5IiwiZW1haWwiOiJ0ZWRkeUBzbWFydGIuY2l0eSJ9.HBZNP7hLpklKbVtMPz_3lzgNNVV68_ZopsHZwsZRQPWzGqA65gD2vVgJT1qPMihkoIgbKYSm-H7tZgsNe3EeT-xhwrkjl7nrptyIgTYDIO2_SBzSIGStLrkE6pnfN79GKuwUoCo8DdW7f5-pfB3o-zrS2BIRSGU0j6prbF-8DWBVE4SzgATuqodzzUre9u2HDm0-Qph1C1Q9lZDPh9i3LZ5ZVcvRxtYAek8ImFsQMVdQljeVuIs0DR34mZCBnZvndqnoqSq78g-mD8vGbepuaJrzlTU6JGhQ3tDZ5WpQAx-XLb5DJ20X_bZvuTD_5sV0ugO2J3ooKSDD2uXTjTJQzw' \
--data-urlencode 'subject_issuer=https://auth.smart-b.io/auth/realms/vault-test' \
--data-urlencode 'audience=token-exchange' \ 
| jq -r '.access_token'
```


# Issuer configuration

## Import a VC template
POST
issuer-api/default/config/templates/ConnectIdCredential

```
{
  "type": [
    "VerifiableCredential",
    "ConnectIdCredential"
  ],
  "@context": [
    "https://www.w3.org/2018/credentials/v1"
  ],
  "credentialSubject": {
    "id": "did:key:example",
    "familyName": "familyNamePlaceholder",
    "firstName": "firstNamePlaceholder",
    "email": "emailPlaceholder"
  }
}
```
