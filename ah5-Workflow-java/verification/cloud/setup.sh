#!/bin/sh
# Prepares the cloud test against the running Java test cloud (default ../../../../Arrowhead-test-clouds/Arrowhead-test-cloud-java):
#   1. certificates of the test systems, signed by the cloud CA (random serials; the cloud's serial file is not
#      touched), with PKCS#12 key- and truststores, and random keystore, identity and database passwords   -> .state/
#   2. Authentication identities, ConsumerAuthorization policies of the test services, and WorkflowManager and the
#      executors added to authorization.verify (needed by every provider-side check)                       -> cloud (cloudctl.py)
# teardown.sh undoes step 2 and restores authorization.verify to the cloud's declared list. Re-running is safe.
set -eu
. "$(dirname -- "$0")/lib.sh"
umask 077
mkdir -p "$STATE/systems"
[ -f "$CLOUD_DIR/security/ca/ca.key" ] || { echo "cloud CA key not found under $CLOUD_DIR/security/ca" >&2; exit 1; }
[ -n "$(cloud_verifiers)" ] || { echo "VERIFIERS not found in the cloud's configure-orchestration-dependency-policies.sh" >&2; exit 1; }

rand() { printf %s "$(openssl rand -hex 18)"; }
[ -f "$STATE/db-root-password" ] || rand > "$STATE/db-root-password"
[ -f "$STATE/db-password" ] || rand > "$STATE/db-password"
printf "ALTER USER 'workflowmanager'@'%%' IDENTIFIED BY '%s';\nFLUSH PRIVILEGES;\n" "$(cat "$STATE/db-password")" > "$STATE/04-db-password.sql"

echo "Certificates (signed by the $CLOUD_NAME CA):"
for entry in $SYSTEMS; do
  name=${entry%%|*}; host=${entry#*|}; dir="$STATE/systems/$name"
  mkdir -p "$dir"
  [ -f "$dir/store-password" ] || rand > "$dir/store-password"
  [ -f "$dir/identity-password" ] || rand > "$dir/identity-password"
  cn="$name.$CLOUD_NAME.$ORGANIZATION.arrowhead.eu"
  if [ ! -f "$dir/system.crt" ]; then
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$dir/system.key" 2>/dev/null
    openssl req -new -sha256 -key "$dir/system.key" -subj "/C=SE/O=$ORGANIZATION/OU=Arrowhead Systems/CN=$cn/dnQualifier=sy" -out "$dir/system.csr"
    printf 'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth,clientAuth\nsubjectAltName=DNS:%s,DNS:localhost,IP:127.0.0.1\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid,issuer\n' "$host" > "$dir/system.ext"
    openssl x509 -req -sha256 -days 30 -in "$dir/system.csr" -CA "$CLOUD_CERTS/ca.crt" -CAkey "$CLOUD_DIR/security/ca/ca.key" \
      -set_serial "0x$(openssl rand -hex 16)" -extfile "$dir/system.ext" -out "$dir/system.crt" 2>/dev/null
    rm -f "$dir/system.csr"
    pw=$(cat "$dir/store-password")
    openssl pkcs12 -export -inkey "$dir/system.key" -in "$dir/system.crt" -certfile "$CLOUD_CERTS/ca.crt" -name "$cn" -passout "pass:$pw" -out "$dir/$name.p12"
    keytool -importcert -noprompt -alias "$CLOUD_NAME.$ORGANIZATION.arrowhead.eu" -file "$CLOUD_CERTS/ca.crt" -storetype PKCS12 \
      -keystore "$dir/truststore.p12" -storepass "$pw" >/dev/null 2>&1
    cp "$CLOUD_CERTS/ca.crt" "$dir/ca.crt"
  fi
  echo "  $cn"
done
# WorkflowConfigurator console: a random password; the system gets only its scrypt hash (LocalCloudManager console format)
wfc="$STATE/systems/WorkflowConfigurator"
if [ ! -f "$wfc/console-password-hash" ]; then
  rand > "$STATE/console-password"
  python3 -c 'import base64,hashlib,os,sys; pw=open(sys.argv[1]).read().strip().encode(); salt=os.urandom(16); h=hashlib.scrypt(pw, salt=salt, n=16384, r=8, p=1, dklen=64); print("scrypt:"+base64.b64encode(salt).decode()+":"+base64.b64encode(h).decode())' \
    "$STATE/console-password" > "$wfc/console-password-hash"
fi
echo "WorkflowConfigurator console password: .state/console-password"
# Docker Desktop maps the host owner into the containers; group/other get nothing
chmod -R go-rwx "$STATE"

SYSOP_PASSWORD=$(sysop_password); export SYSOP_PASSWORD
in_cloud /h/cloudctl.py setup
