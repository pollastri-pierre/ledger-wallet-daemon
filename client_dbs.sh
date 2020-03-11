#!/usr/bin/env bash
# This script could be useful to create all dbs
# from database.db which contains users' names
host=$1
port=$2
prefix=$3
drop_mode=$4

command=createdb
if [ -n "$drop_mode" ]; then
    echo "!!!!!!!!!!!!!!!!!!!!!"
    echo "!!DROPPING DBS MODE!!"
    echo "!!!!!!!!!!!!!!!!!!!!!"
    command=dropdb
fi

echo "==> PostgreSQL server infos: host " $host ", port " $port

# Retrieve all users' names
names="$(sqlite3 database.db -cmd 'select name from pools;' .quit)"

# Replace endlines with spaces
names=`echo "$names" | tr '\n' ' '`

read -ra array <<< "$names"

# Create all DBs with users' names as database names
for elem in "${array[@]}"
do
    echo "$command -h $host -p $port -U ledger ${prefix}_${elem}"
    $command -h $host -p $port -U ledger ${prefix}_${elem}
done



