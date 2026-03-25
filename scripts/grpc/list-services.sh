#!/usr/bin/env bash
set -euo pipefail

TARGET="${GRPC_TARGET:-host.docker.internal:9090}"

docker run --rm fullstorydev/grpcurl -plaintext "$TARGET" list
