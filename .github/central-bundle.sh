#!/usr/bin/env bash
# Builds bundle.zip for the Maven Central Portal from the release build: the pom, the jar, its sources and docs jars,
# each signed with the key $FINGERPRINT (passphrase $PASSPHRASE) and with MD5 and SHA-1 checksums.
set -euo pipefail

dir="bundle/io/stepfunc/dnp3c/$VERSION"
rm -rf bundle bundle.zip
mkdir -p "$dir"
cp pom.xml "$dir/dnp3c-$VERSION.pom"
cp "target/dnp3c-$VERSION.jar" "target/dnp3c-$VERSION-sources.jar" "target/dnp3c-$VERSION-javadoc.jar" "$dir/"

cd "$dir"
for file in *.jar *.pom; do
  gpg --batch --yes --pinentry-mode loopback --local-user "$FINGERPRINT" --passphrase "$PASSPHRASE" \
    --armor --detach-sign "$file"
  md5sum "$file" | cut -d' ' -f1 > "$file.md5"
  sha1sum "$file" | cut -d' ' -f1 > "$file.sha1"
done

cd - > /dev/null
(cd bundle && zip -qr ../bundle.zip io)
