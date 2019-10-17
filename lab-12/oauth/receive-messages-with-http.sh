#!/usr/bin/env bash

CONNECT_BRIDGE="$(kubectl get routes my-bridge -o jsonpath='{.status.ingress[0].host}')"

echo ""
echo "Creating the consumer-group"
echo ""

cat <<EOF | http ${CONNECT_BRIDGE}/consumers/my-bridge Content-Type:application/vnd.kafka.v2+json
{
    "name": "my-client",
    "format": "json",
    "auto.offset.reset": "earliest",
    "enable.auto.commit": "true",
    "fetch.min.bytes": "512",
    "consumer.request.timeout.ms": "30000"
}
EOF

echo ""
echo "Subscribing to topics"
echo ""

cat <<EOF | http ${CONNECT_BRIDGE}/consumers/my-bridge/instances/my-client/subscription Content-Type:application/vnd.kafka.v2+json
{
    "topics": [
        "my-topic"
    ]
}
EOF

echo ""
echo "We should now be subscribed to the topic"
echo "Getting the messages"
echo ""

while http ${CONNECT_BRIDGE}/consumers/my-bridge/instances/my-client/records Accept:application/vnd.kafka.json.v2+json
do
  sleep 5
done
