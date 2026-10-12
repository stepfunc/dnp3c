#!/usr/bin/env bash
# Publishes bundle.zip to Maven Central and waits until it's published, failing if validation or publishing fails or
# takes longer than 45 minutes. A version that's already published is skipped, so a release can be rerun; a rerun while
# a timed out deployment is still publishing uploads it again, which fails, and the next rerun once it's published skips.
set -euo pipefail
deadline=$((SECONDS + 45 * 60))

api=https://central.sonatype.com/api/v1/publisher
auth="Authorization: Bearer $(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_TOKEN" | base64 -w 0)"

published=$(curl -sf --max-time 60 "$api/published?namespace=io.stepfunc&name=dnp3c&version=$VERSION" -H "$auth" | jq -r .published)
if [ "$published" = true ]; then
  echo "io.stepfunc:dnp3c:$VERSION is already published, skipping"
  exit 0
fi

status=$(curl -s --max-time 600 -o response.txt -w '%{http_code}' -X POST "$api/upload?publishingType=AUTOMATIC" \
  -H "$auth" -F "bundle=@bundle.zip;type=application/octet-stream")
echo "Upload: HTTP $status: $(cat response.txt)"
[ "$status" -ge 200 ] && [ "$status" -lt 300 ]
# the response is the deployment's id, as plain text
id=$(tr -d '[:space:]' < response.txt)

while [ $SECONDS -lt $deadline ]; do
  curl -sf --max-time 60 -X POST "$api/status?id=$id" -H "$auth" > status.json
  state=$(jq -r .deploymentState status.json)
  echo "Deployment $id: $state"
  case "$state" in
    PUBLISHED) exit 0 ;;
    FAILED) jq . status.json; exit 1 ;;
  esac
  sleep 30
done
echo "Deployment $id wasn't published within 45 minutes"
exit 1
