#!/usr/bin/env bash
cd squid
docker build ./ -t ledgerhq/walletdaemon-squid:3.5.27-2
docker push ledgerhq/walletdaemon-squid:3.5.27-2
